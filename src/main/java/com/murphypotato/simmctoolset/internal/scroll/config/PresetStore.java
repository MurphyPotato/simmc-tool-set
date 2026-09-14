package com.murphypotato.simmctoolset.internal.scroll.config;

import com.google.gson.*;
import com.murphypotato.simmctoolset.internal.scroll.domain.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Atomic, metric-free preset snapshots. */
public final class PresetStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file; private final List<PresetPlan> presets = new ArrayList<>();
    private boolean readOnly; private String warning;
    public PresetStore(Path file){this.file=Objects.requireNonNull(file);load();}
    public synchronized List<PresetPlan> list(){return List.copyOf(presets);}
    public synchronized boolean isReadOnly(){return readOnly;}
    public synchronized Optional<String> warning(){return Optional.ofNullable(warning);}
    public synchronized void save(PresetPlan preset)throws IOException{ensureWritable();Objects.requireNonNull(preset);List<PresetPlan> next=new ArrayList<>(presets);next.removeIf(p->p.name().equals(preset.name()));next.add(preset);write(next);presets.clear();presets.addAll(next);}
    public synchronized void rename(String oldName,String newName)throws IOException{ensureWritable();if(oldName==null||newName==null)throw new IllegalArgumentException("预设名称无效");PresetPlan p=presets.stream().filter(x->x.name().equals(oldName)).findFirst().orElseThrow(()->new IllegalArgumentException("预设不存在"));save(new PresetPlan(newName,p.recipe(),p.batches()));}
    public synchronized void delete(String name)throws IOException{ensureWritable();List<PresetPlan>next=new ArrayList<>(presets);next.removeIf(p->p.name().equals(name));write(next);presets.clear();presets.addAll(next);}
    private void ensureWritable(){if(readOnly)throw new IllegalStateException("预设文件处于只读错误模式："+warning);}
    private void load(){if(!Files.exists(file))return;try{JsonElement root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8));JsonArray a=root.isJsonObject()?root.getAsJsonObject().getAsJsonArray("presets"):root.getAsJsonArray();for(JsonElement e:a)presets.add(decode(e.getAsJsonObject()));}catch(Exception e){readOnly=true;warning="预设文件损坏或不可读，未覆盖原文件："+e.getMessage();presets.clear();}}
    private static PresetPlan decode(JsonObject o){List<RotationBatch>batches=new ArrayList<>();if(o.has("batches"))for(JsonElement e:o.getAsJsonArray("batches")){JsonObject x=e.getAsJsonObject();Map<String,Integer>m=new LinkedHashMap<>();for(var q:x.getAsJsonObject("materials").entrySet()){int n=q.getValue().getAsInt();if(n<0)throw new IllegalArgumentException("negative material");m.put(q.getKey(),n);}CraftPlan p=new CraftPlan(m.toString(),m,ElementAmounts.zero(),0,0,m.values().stream().mapToInt(Integer::intValue).sum(),0,m.size(),0);batches.add(new RotationBatch(p,x.get("crafts").getAsInt()));}return new PresetPlan(o.get("name").getAsString(),o.get("recipe").getAsString(),batches);}
    private static void writeJson(JsonArray a,List<PresetPlan> list){for(PresetPlan p:list){JsonObject o=new JsonObject();o.addProperty("name",p.name());o.addProperty("recipe",p.recipe());JsonArray b=new JsonArray();for(RotationBatch rb:p.batches()){JsonObject x=new JsonObject();x.addProperty("crafts",rb.crafts());JsonObject m=new JsonObject();rb.plan().materials().forEach(m::addProperty);x.add("materials",m);b.add(x);}o.add("batches",b);a.add(o);}}
    private void write(List<PresetPlan> list)throws IOException{Files.createDirectories(file.toAbsolutePath().getParent());JsonArray a=new JsonArray();writeJson(a,list);Path t=file.resolveSibling(file.getFileName()+"."+UUID.randomUUID()+".tmp");try{Files.writeString(t,JSON.toJson(a)+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);try{Files.move(t,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(t,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(t);}}
}
