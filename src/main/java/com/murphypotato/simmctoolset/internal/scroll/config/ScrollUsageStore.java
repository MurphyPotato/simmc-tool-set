package com.murphypotato.simmctoolset.internal.scroll.config;

import com.google.gson.*;
import com.murphypotato.simmctoolset.internal.scroll.domain.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Durable UUID/date-scoped ledger. Mutations use validate-then-atomic-replace. */
public final class ScrollUsageStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private final Path file; private final Clock clock;
    private final Map<UUID, Map<LocalDate, Ledger>> players = new LinkedHashMap<>();
    private boolean readOnly; private String warning;

    public ScrollUsageStore(Path file) { this(file, Clock.system(BEIJING)); }
    public ScrollUsageStore(Path file, Clock clock) { this.file=Objects.requireNonNull(file); this.clock=Objects.requireNonNull(clock); load(); }
    public synchronized boolean isReadOnly() { return readOnly; }
    public synchronized Optional<String> warning() { return Optional.ofNullable(warning); }
    public synchronized UsageSnapshot snapshot(UUID playerId) {
        Objects.requireNonNull(playerId); LocalDate d=today(); Ledger l=ledger(playerId,d);
        return new UsageSnapshot(playerId,d,l==null?0:l.revision,l==null?0:l.currentM,l==null?Map.of():l.totals,readOnly,warning);
    }
    public synchronized List<UsageRecord> history(UUID playerId) { return players.getOrDefault(playerId,Map.of()).values().stream().flatMap(l->l.records.stream()).toList(); }
    public synchronized List<UsageAuditEntry> audits(UUID playerId) { return players.getOrDefault(playerId,Map.of()).values().stream().flatMap(l->l.audits.stream()).toList(); }
    public synchronized long revision(UUID playerId) { return snapshot(playerId).revision(); }

    public synchronized CommitResult commit(UsageCommitRequest r) throws IOException {
        Objects.requireNonNull(r); ensureWritable(); LocalDate d=today();
        if (!d.equals(r.expectedDate())) throw new StaleCommitException("日期已变化");
        UsageRecord prior=findTransaction(r.transactionId()); if(prior!=null) return new CommitResult(prior,true,revision(r.playerId()));
        Ledger old=ledger(r.playerId(),d); long rev=old==null?0:old.revision;
        if(rev!=r.expectedRevision()) throw new StaleCommitException("使用记录版本已变化");
        Ledger next=old==null?new Ledger():old.copy(); next.revision=Math.addExact(rev,1); next.currentM=r.afterM();
        r.actualMaterialUsage().forEach((k,v)->next.totals.merge(k,v,ScrollUsageStore::addExact));
        UsageRecord rec=new UsageRecord(r.transactionId(),r.playerId(),d,Instant.now(clock),r.recipe(),r.orderedPlanInputs(),r.requestedCrafts(),r.actualMaterialUsage(),r.beforeM(),r.afterM(),r.autoMode(),r.modified());
        next.records.add(rec); persist(r.playerId(),d,next); players.computeIfAbsent(r.playerId(),x->new LinkedHashMap<>()).put(d,next);
        return new CommitResult(rec,false,next.revision);
    }

    public synchronized UsageSnapshot edit(UUID playerId, LocalDate expectedDate, long expectedRevision, Map<String,Integer> newTotals,
                                            int newM, boolean acknowledged, String reason) throws IOException {
        ensureWritable(); if(!acknowledged)throw new IllegalArgumentException("手动编辑必须明确确认");
        if(!today().equals(expectedDate))throw new StaleCommitException("日期已变化");
        Map<String,Integer> checked=UsageCommitRequest.nonnegative(newTotals); Ledger old=ledger(playerId,expectedDate); long rev=old==null?0:old.revision;
        if(rev!=expectedRevision)throw new StaleCommitException("使用记录版本已变化");
        if (newM < 0) throw new IllegalArgumentException("M 不能为负数");
        Ledger next=old==null?new Ledger():old.copy(); Map<String,Integer> before=next.totals; next.totals=new LinkedHashMap<>(checked); next.currentM=newM; next.revision=Math.addExact(rev,1);
        next.audits.add(new UsageAuditEntry(UUID.randomUUID(),playerId,Instant.now(clock),before,checked,reason==null||reason.isBlank()?"manual edit":reason));
        persist(playerId,expectedDate,next); players.computeIfAbsent(playerId,x->new LinkedHashMap<>()).put(expectedDate,next); return snapshot(playerId);
    }
    // Old call sites cannot safely identify a player; these adapters never write.
    public synchronized int count(String material) { return 0; }
    public synchronized Map<String,Integer> snapshot() { return Map.of(); }
    public synchronized void add(Map<String,Integer> usage) { throw new UnsupportedOperationException("必须提供 player UUID 和事务上下文"); }
    public synchronized void set(String material,int value) { throw new UnsupportedOperationException("必须提供 player UUID 和审计确认"); }

    private void ensureWritable(){if(readOnly)throw new IllegalStateException("使用记录处于只读错误模式："+warning);}
    private LocalDate today(){return LocalDate.now(clock.withZone(BEIJING));}
    private Ledger ledger(UUID p,LocalDate d){Map<LocalDate,Ledger> m=players.get(p);return m==null?null:m.get(d);}
    private UsageRecord findTransaction(UUID tx){return players.values().stream().flatMap(m->m.values().stream()).flatMap(l->l.records.stream()).filter(r->tx.equals(r.transactionId())).findFirst().orElse(null);}
    private void persist(UUID player,LocalDate date,Ledger ledger)throws IOException{
        Map<UUID,Map<LocalDate,Ledger>> candidate=new LinkedHashMap<>(players); Map<LocalDate,Ledger> ds=new LinkedHashMap<>(candidate.getOrDefault(player,Map.of())); ds.put(date,ledger);candidate.put(player,ds);
        JsonObject root=new JsonObject();root.addProperty("version",2);JsonObject ps=new JsonObject();
        candidate.forEach((id,by)->{JsonObject dates=new JsonObject();by.forEach((day,l)->dates.add(day.toString(),encode(l)));JsonObject po=new JsonObject();po.add("dates",dates);ps.add(id.toString(),po);});root.add("players",ps);
        Path parent=file.toAbsolutePath().getParent();Files.createDirectories(parent);Path tmp=file.resolveSibling(file.getFileName()+"."+UUID.randomUUID()+".tmp");
        try{Files.writeString(tmp,JSON.toJson(root)+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
    }
    private void load(){if(!Files.exists(file))return;try{JsonObject root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();if(root.get("version").getAsInt()!=2)throw new IOException("不支持的使用记录版本");for(var pe:root.getAsJsonObject("players").entrySet()){UUID p=UUID.fromString(pe.getKey());Map<LocalDate,Ledger> ds=new LinkedHashMap<>();for(var de:pe.getValue().getAsJsonObject().getAsJsonObject("dates").entrySet())ds.put(LocalDate.parse(de.getKey()),decode(de.getValue().getAsJsonObject()));players.put(p,ds);}}catch(Exception e){readOnly=true;warning="使用记录损坏或不可读，未覆盖原文件："+e.getMessage();players.clear();}}
    private static JsonObject encode(Ledger l){JsonObject o=new JsonObject();o.addProperty("revision",l.revision);o.addProperty("currentM",l.currentM);JsonObject t=new JsonObject();l.totals.forEach(t::addProperty);o.add("totals",t);JsonArray rs=new JsonArray();for(UsageRecord r:l.records){JsonObject x=new JsonObject();x.addProperty("transactionId",r.transactionId().toString());x.addProperty("playerId",r.playerId().toString());x.addProperty("beijingDate",r.beijingDate().toString());x.addProperty("timestamp",r.timestamp().toString());x.addProperty("recipe",r.recipe());x.addProperty("requestedCrafts",r.requestedCrafts());x.addProperty("beforeM",r.beforeM());x.addProperty("afterM",r.afterM());x.addProperty("autoMode",r.autoMode());x.addProperty("modified",r.modified());JsonArray inputs=new JsonArray();for(UsagePlanInput i:r.orderedPlanInputs()){JsonObject q=new JsonObject();q.addProperty("quantity",i.quantity());JsonObject m=new JsonObject();i.materials().forEach(m::addProperty);q.add("materials",m);inputs.add(q);}x.add("orderedPlanInputs",inputs);JsonObject used=new JsonObject();r.actualMaterialUsage().forEach(used::addProperty);x.add("actualMaterialUsage",used);rs.add(x);}o.add("records",rs);JsonArray as=new JsonArray();for(UsageAuditEntry a:l.audits){JsonObject x=new JsonObject();x.addProperty("transactionId",a.transactionId().toString());x.addProperty("playerId",a.playerId().toString());x.addProperty("timestamp",a.timestamp().toString());x.add("oldTotals",toObject(a.oldTotals()));x.add("newTotals",toObject(a.newTotals()));x.addProperty("reason",a.reason());as.add(x);}o.add("audits",as);return o;}
    private static JsonObject toObject(Map<String,Integer> m){JsonObject o=new JsonObject();m.forEach(o::addProperty);return o;}
    private static Ledger decode(JsonObject o){Ledger l=new Ledger();l.revision=nonnegativeLong(o,"revision");l.currentM=nonnegativeInt(o,"currentM");for(var e:o.getAsJsonObject("totals").entrySet())l.totals.put(e.getKey(),nonnegativeInt(e.getValue()));for(var e:o.getAsJsonArray("records")){JsonObject x=e.getAsJsonObject();l.records.add(new UsageRecord(UUID.fromString(x.get("transactionId").getAsString()),UUID.fromString(x.get("playerId").getAsString()),LocalDate.parse(x.get("beijingDate").getAsString()),Instant.parse(x.get("timestamp").getAsString()),x.get("recipe").getAsString(),decodeInputs(x.getAsJsonArray("orderedPlanInputs")),nonnegativeInt(x,"requestedCrafts"),map(x.getAsJsonObject("actualMaterialUsage")),nonnegativeInt(x,"beforeM"),nonnegativeInt(x,"afterM"),x.get("autoMode").getAsBoolean(),x.get("modified").getAsBoolean()));}if(o.has("audits"))for(var e:o.getAsJsonArray("audits")){JsonObject x=e.getAsJsonObject();l.audits.add(new UsageAuditEntry(UUID.fromString(x.get("transactionId").getAsString()),UUID.fromString(x.get("playerId").getAsString()),Instant.parse(x.get("timestamp").getAsString()),map(x.getAsJsonObject("oldTotals")),map(x.getAsJsonObject("newTotals")),x.get("reason").getAsString()));}return l;}
    private static List<UsagePlanInput> decodeInputs(JsonArray a){List<UsagePlanInput> out=new ArrayList<>();for(JsonElement e:a){JsonObject x=e.getAsJsonObject();out.add(new UsagePlanInput(nonnegativeInt(x,"quantity"),map(x.getAsJsonObject("materials"))));}return out;}
    private static Map<String,Integer> map(JsonObject o){Map<String,Integer>m=new LinkedHashMap<>();for(var e:o.entrySet())m.put(e.getKey(),nonnegativeInt(e.getValue()));return m;}
    private static int nonnegativeInt(JsonObject o,String k){return nonnegativeInt(o.get(k));}private static int nonnegativeInt(JsonElement e){int n=e.getAsInt();if(n<0)throw new IllegalArgumentException("negative count");return n;}private static long nonnegativeLong(JsonObject o,String k){long n=o.get(k).getAsLong();if(n<0)throw new IllegalArgumentException("negative revision");return n;}private static int addExact(int a,int b){return Math.addExact(a,b);}
    private static final class Ledger{long revision;int currentM;Map<String,Integer>totals=new LinkedHashMap<>();List<UsageRecord>records=new ArrayList<>();List<UsageAuditEntry>audits=new ArrayList<>();Ledger copy(){Ledger x=new Ledger();x.revision=revision;x.currentM=currentM;x.totals=new LinkedHashMap<>(totals);x.records=new ArrayList<>(records);x.audits=new ArrayList<>(audits);return x;}}
    public record CommitResult(UsageRecord record,boolean duplicate,long revision){}
    public static class StaleCommitException extends IllegalStateException{public StaleCommitException(String message){super(message);}}
}
