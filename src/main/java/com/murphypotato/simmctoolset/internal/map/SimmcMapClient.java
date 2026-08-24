package com.murphypotato.simmctoolset.internal.map;

import com.murphypotato.simmctoolset.internal.map.model.MapSnapshot;
import com.murphypotato.simmctoolset.internal.map.model.MapPoint;
import com.murphypotato.simmctoolset.internal.map.model.OnlinePlayerEntry;
import com.murphypotato.simmctoolset.internal.map.command.SimmcMapCommand;
import com.murphypotato.simmctoolset.internal.map.gui.WorldMapUiController;
import com.murphypotato.simmctoolset.internal.map.integration.XaeroCompatibility;
import com.murphypotato.simmctoolset.internal.map.integration.WorldRuntimeState;
import com.murphypotato.simmctoolset.internal.map.integration.XaeroWaypointBridge;
import com.murphypotato.simmctoolset.map.XaeroCapabilityProbe;
import com.murphypotato.simmctoolset.internal.map.cache.*;
import com.murphypotato.simmctoolset.internal.map.config.*;
import com.murphypotato.simmctoolset.internal.map.network.*;
import com.murphypotato.simmctoolset.internal.map.parse.*;
import com.murphypotato.simmctoolset.internal.map.render.*;
import com.murphypotato.simmctoolset.internal.map.render.LayerRenderer;
import com.murphypotato.simmctoolset.internal.map.render.WorldMapOverlayRenderer;
import com.murphypotato.simmctoolset.internal.map.render.WorldMapInputRouting;
import com.murphypotato.simmctoolset.internal.map.render.WorldMapRenderSession;
import com.murphypotato.simmctoolset.internal.map.render.WorldMapSessionSlot;
import com.murphypotato.simmctoolset.internal.map.render.RequestContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import xaero.common.HudMod;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.render.module.ModuleRenderContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.*;

/** Xaero-specific runtime started only by the Tool Set entrypoint. */
public final class SimmcMapClient {
    public static final String MOD_ID = "simmc_tool_set";
    private static final Identifier GENERIC_ICON = Identifier.of("simmc_tool_set", "generic-fallback.png");
    private static final ColoredCommandRenderPlanner COLORED_COMMANDS = new ColoredCommandRenderPlanner();
    private static final WorldMapOverlayRenderer WORLD_MAP =
            new WorldMapOverlayRenderer(new MapSnapshot(List.of()));
    private static final WorldMapSessionSlot WORLD_MAP_SESSION = new WorldMapSessionSlot();
    private static final AtomicReference<WorldMapOverlayRenderer.HitResult> SELECTED = new AtomicReference<>();
    private static final WorldMapUiController WORLD_MAP_UI = new WorldMapUiController();
    private static volatile boolean fullWorldMode;
    private static volatile double fullWorldFloor = 0.0625;
    private static final AtomicReference<RuntimeState> RUNTIME = new AtomicReference<>();
    private static SimmcMapConfig config;
    private static Path configPath;
    private static volatile String runtimeStatus = "等待连接 play.simmc.cn";

    public static void initialize() {
        if (config != null) return;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = SimmcMapClient.class.getClassLoader();
        WorldRuntimeState.initialize(XaeroCapabilityProbe.probe(loader), loader);
        Path configDir = FabricLoader.getInstance().getConfigDir();
        configPath = configDir.resolve("simmc-tool-set").resolve("map.json");
        config = SimmcMapConfig.loadOrImport(configPath,
                FabricLoader.getInstance().isModLoaded("simmcmap"),
                configDir.resolve("simmc-tool-set-map.json"),
                configDir.resolve("simmcmap.json"));
        WORLD_MAP_UI.applyConfig(config);
        SimmcMapCommand.register();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String address = client.getCurrentServerEntry() == null ? "" : client.getCurrentServerEntry().address;
            new ServerActivationService(config).resolveFor(address).ifPresentOrElse(
                    profile -> startRuntime(client, profile),
                    () -> runtimeStatus = "当前服务器未启用 SIMMC 地图（仅 play.simmc.cn 或自定义服务器）");
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopRuntime());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stopRuntime());
        ClientTickEvents.END_CLIENT_TICK.register(client -> publishRuntime(client));
    }

    public static void close() {
        stopRuntime();
    }

    private static void startRuntime(MinecraftClient client, ConnectionProfile profile) {
        stopRuntime();
        runtimeStatus = "正在连接地图数据源";
        try {
            Path cache = FabricLoader.getInstance().getGameDir().resolve("simmc-tool-set-map-cache");
            MapCacheMigration.importIfAbsent(cache,
                    FabricLoader.getInstance().getGameDir().resolve("simmcmap-cache"));
            HttpClient.Builder httpBuilder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8));
            SquaremapHttpClient http = new SquaremapHttpClient(httpBuilder.build(), Duration.ofSeconds(12), 32L * 1024 * 1024, "SIMMC-Map/1.0.0");
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "simmc-refresh-scheduler"));
            ExecutorService parser = Executors.newFixedThreadPool(2, r -> daemon(r, "simmc-refresh-parser"));
            RefreshCoordinator coordinator = new RefreshCoordinator(profile.worldKey(),
                    new HttpSquaremapDataSource(URI.create(profile.baseUrl()), profile.worldKey(), http),
                    new SquaremapSettingsParser(), new SquaremapMarkersParser(), new PlayersParser(),
                    new SnapshotDiskCache(cache.resolve("snapshot.bin")), Clock.systemUTC(),
                    (action, delay) -> { ScheduledFuture<?> future = scheduler.schedule(action, delay.toMillis(), TimeUnit.MILLISECONDS); return () -> future.cancel(false); },
                    parser, Math::random);
            RuntimeState state = new RuntimeState(profile, cache, http, scheduler, parser, coordinator);
            RUNTIME.set(state);
            coordinator.restoreFromDisk();
            coordinator.startScheduling();
            runtimeStatus = "已激活，等待地图数据";
            publishRuntime(client);
        } catch (RuntimeException failure) {
            XaeroCompatibility.disable("SIMMC 地图数据连接初始化失败", failure);
            runtimeStatus = "地图数据源启动失败，请查看诊断日志";
            stopRuntime();
        }
    }

    private static void publishRuntime(MinecraftClient client) {
        RuntimeState state = RUNTIME.get();
        if (state == null) return;
        var settings = state.coordinator.settings();
        var snapshot = state.coordinator.snapshot();
        try {
            if (settings != null && !state.sessionInstalled) {
                ExecutorService tilesExecutor = Executors.newFixedThreadPool(8, r -> daemon(r, "simmc-tile"));
                var tiles = new SquaremapTileRenderer(state.profileId(), settings,
                        new TileDiskCache(state.cache.resolve("tiles"), (long) config.tileDiskCacheMiB() * 1024 * 1024, 8 * 1024 * 1024, 128),
                        new HttpTileSource(state.profile, state.http), new PngTileDecoder(8 * 1024 * 1024, 4_194_304),
                        new MinecraftTextureBackend(client.getTextureManager()), tilesExecutor, true,
                        System::currentTimeMillis, new TileRendererLimits(8, 32, 256, 10_000),
                        TileRefreshPolicy.standardSeconds(config.tileRevalidateSeconds()));
                var icons = new IconResourceLoader(URI.create(state.profile.baseUrl()), state.http,
                        new IconDiskCache(state.cache.resolve("icons")), Clock.systemUTC());
                var session = new WorldMapRenderSession(tiles, icons,
                        new IconTextureRegistry(64, new MinecraftIconTextureBackend(client.getTextureManager())), 4, 2);
                installWorldMapSession(session, new WorldMapRenderSession.SessionInput(state.profileId(), state.profile, settings, snapshot));
                state.sessionInstalled = true;
            } else if (settings != null && state.sessionInstalled && (snapshot != state.lastSnapshot || settings != state.lastSettings)) {
                updateWorldMapSession(new WorldMapRenderSession.SessionInput(state.profileId(), state.profile, settings, snapshot));
            }
            if (snapshot != state.lastSnapshot) { publishWorldMapSnapshot(snapshot); state.lastSnapshot = snapshot; }
            int markerCount = snapshot == null ? 0 : snapshot.layers().stream()
                    .mapToInt(layer -> layer.markers().size()).sum();
            if (markerCount > 0) runtimeStatus = "已收到 " + markerCount + " 个地图标记";
            else if (settings != null) runtimeStatus = "已连接，等待公开地图标记";
            List<OnlinePlayerEntry> players = state.coordinator.players();
            if (players != state.lastPlayers || state.playersAvailable != state.coordinator.playersAvailable()) {
                publishOnlinePlayers(players, state.coordinator.playersAvailable()); state.lastPlayers = players; state.playersAvailable = state.coordinator.playersAvailable();
            }
            state.lastSettings = settings;
        } catch (RuntimeException failure) { XaeroCompatibility.disable("SIMMC 数据发布失败", failure); }
    }

    private static void stopRuntime() {
        RuntimeState state = RUNTIME.getAndSet(null);
        closeWorldMapSession();
        if (state == null) {
            runtimeStatus = "未连接地图数据源";
            return;
        }
        try { state.coordinator.close(); } catch (RuntimeException ignored) { }
        state.scheduler.shutdownNow(); state.parser.shutdownNow();
        try { state.http.close(); } catch (RuntimeException ignored) { }
        publishOnlinePlayers(List.of(), false);
        publishWorldMapSnapshot(new MapSnapshot(List.of()));
        saveUserSettings();
        runtimeStatus = "未连接地图数据源";
    }

    private static Thread daemon(Runnable action, String name) { Thread thread = new Thread(action, name); thread.setDaemon(true); return thread; }

    private static final class RuntimeState {
        final ConnectionProfile profile; final Path cache; final SquaremapHttpClient http; final ScheduledExecutorService scheduler; final ExecutorService parser; final RefreshCoordinator coordinator;
        MapSnapshot lastSnapshot; com.murphypotato.simmctoolset.internal.map.model.SquaremapWorldSettings lastSettings; List<OnlinePlayerEntry> lastPlayers; boolean playersAvailable; boolean sessionInstalled;
        RuntimeState(ConnectionProfile profile, Path cache, SquaremapHttpClient http, ScheduledExecutorService scheduler, ExecutorService parser, RefreshCoordinator coordinator) { this.profile = profile; this.cache = cache; this.http = http; this.scheduler = scheduler; this.parser = parser; this.coordinator = coordinator; }
        String profileId() { return Integer.toHexString((profile.baseUrl() + '|' + profile.worldKey()).hashCode()); }
    }

    public static WorldMapOverlayRenderer worldMapOverlay() {
        return WORLD_MAP_SESSION.current().map(WorldMapRenderSession::overlay).orElse(WORLD_MAP);
    }

    public static void publishWorldMapSnapshot(MapSnapshot snapshot) {
        WORLD_MAP.acceptSnapshot(snapshot);
        WORLD_MAP_SESSION.updateSnapshot(snapshot);
        WORLD_MAP_UI.updateSnapshot(snapshot);
    }

    public static void publishOnlinePlayers(List<OnlinePlayerEntry> players, boolean available) {
        WORLD_MAP_UI.updatePlayers(players, available);
    }

    /** Atomic connection-specific entry point for Task 13. */
    public static void installWorldMapSession(WorldMapRenderSession session,
                                              WorldMapRenderSession.SessionInput input) {
        WORLD_MAP_SESSION.install(session, input);
    }

    public static boolean updateWorldMapSession(WorldMapRenderSession.SessionInput input) {
        return WORLD_MAP_SESSION.update(input);
    }

    public static boolean invalidateWorldMapGpuResources() {
        return WORLD_MAP_SESSION.invalidateGpuResources();
    }

    public static void closeWorldMapSession() {
        WORLD_MAP_SESSION.close();
    }

    public static void renderWorldMapSurface(DrawContext context, WorldMapOverlayRenderer.View view,
                                             RequestContext requestContext) {
        if (!WORLD_MAP_UI.toolbar().worldMapEnabled()) return;
        worldMapOverlay().setHiddenLayers(WORLD_MAP_UI.toolbar().layerPanel().hiddenLayerIds());
        var session = WORLD_MAP_SESSION.current();
        if (session.isPresent()) {
            session.orElseThrow().render(new WorldMapRenderSession.Surface() {
                @Override public void drawTile(WorldMapRenderSession.TileDraw tile) {
                    if (WORLD_MAP_UI.toolbar().worldBackgroundEnabled()) SimmcMapClient.drawTile(context, tile);
                }

                @Override public void drawMarkers(List<LayerRenderer.DrawCommand> commands,
                                                  WorldMapRenderSession.IconLookup icons) {
                    drawCommands(context, commands, icons);
                }
            }, view, requestContext);
            return;
        }
        WORLD_MAP.render((ignored, commands) -> drawCommands(context, commands, key -> java.util.Optional.empty()), view);
    }

    public static void renderWorldMapUi(DrawContext context, int mouseX, int mouseY,
                                        WorldMapOverlayRenderer.View view) {
        WORLD_MAP_UI.render(context, mouseX, mouseY, worldMapOverlay(), view);
    }

    /** Draws after Xaero's minimap PIP and before its frame, arrows, labels, and waypoints. */
    public static void renderMinimap(DrawContext context, MinimapSession minimapSession,
                                     ModuleRenderContext renderContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (config == null || client.player == null || client.world == null
                || client.world.getRegistryKey() != net.minecraft.world.World.OVERWORLD
                || !WORLD_MAP_UI.toolbar().worldMapEnabled()) return;
        int size = Math.max(32, renderContext.w);
        boolean circular = HudMod.INSTANCE != null && HudMod.INSTANCE.getSettings().minimapShape == 0;
        WorldMapOverlayRenderer overlay = worldMapOverlay();
        overlay.setHiddenLayers(config.hiddenLayerIds());
        MinimapOverlayRenderer.Plan plan = new MinimapOverlayRenderer(overlay).plan(
                client.player.getX(), client.player.getZ(), renderContext.screenWidth,
                renderContext.screenHeight, renderContext.x, renderContext.y, size,
                minimapSession.getProcessor().getMinimapZoom(), circular);
        MinimapOverlayRenderer.Clip clip = plan.clip();
        context.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
        try {
            var liveSession = WORLD_MAP_SESSION.current();
            if (liveSession.isPresent()) {
                liveSession.orElseThrow().render(new WorldMapRenderSession.Surface() {
                    @Override public void drawTile(WorldMapRenderSession.TileDraw tile) {
                        if (config.minimapBackgroundEnabled()) drawTileClipped(context, tile, clip);
                    }

                    @Override public void drawMarkers(List<LayerRenderer.DrawCommand> commands,
                                                      WorldMapRenderSession.IconLookup icons) {
                        drawCommands(context, commands, icons, clip);
                    }
                }, plan.view(), RequestContext.MINIMAP_STILL);
            } else {
                drawCommands(context, plan.commands(), key -> java.util.Optional.empty(), clip);
            }
        } finally {
            context.disableScissor();
        }
    }

    public static String createWaypoint(com.murphypotato.simmctoolset.internal.map.model.SearchEntry entry) {
        return XaeroWaypointBridge.request(entry)
                .map(XaeroWaypointBridge::create)
                .map(XaeroWaypointBridge::message)
                .orElse("该地点没有公开坐标");
    }

    public static String createWaypoint(OnlinePlayerEntry player) {
        return XaeroWaypointBridge.request(player)
                .map(XaeroWaypointBridge::create)
                .map(XaeroWaypointBridge::message)
                .orElse("该玩家没有公开主世界坐标");
    }

    public static void requestRefresh() {
        RuntimeState state = RUNTIME.get();
        if (state == null) return;
        state.coordinator.refreshMarkersNow();
        state.coordinator.refreshPlayersNow();
        WORLD_MAP_SESSION.revalidateCurrentViewport();
    }

    public static void saveUserSettings() {
        if (config == null || configPath == null) return;
        config = config.withFavoriteKeys(WORLD_MAP_UI.favoriteKeys()).withMapVisibility(
                WORLD_MAP_UI.toolbar().worldMapEnabled(), WORLD_MAP_UI.toolbar().worldBackgroundEnabled(),
                WORLD_MAP_UI.toolbar().minimapBackgroundEnabled(),
                WORLD_MAP_UI.toolbar().layerPanel().hiddenLayerIds());
        config.save(configPath);
    }

    public static boolean worldMapEnabled() { return WORLD_MAP_UI.toolbar().worldMapEnabled(); }
    public static boolean worldBackgroundEnabled() { return WORLD_MAP_UI.toolbar().worldBackgroundEnabled(); }
    public static boolean minimapBackgroundEnabled() { return WORLD_MAP_UI.toolbar().minimapBackgroundEnabled(); }
    public static String runtimeStatus() { return runtimeStatus; }
    public static void toggleWorldMap() { WORLD_MAP_UI.toolbar().toggleWorldMap(); saveUserSettings(); }
    public static void toggleWorldBackground() { WORLD_MAP_UI.toolbar().toggleWorldBackground(); saveUserSettings(); }
    public static void toggleMinimapBackground() { WORLD_MAP_UI.toolbar().toggleMinimapBackground(); saveUserSettings(); }

    public static boolean onWorldMapClick(double mouseX, double mouseY, int button,
                                          WorldMapOverlayRenderer.View view) {
        if (WORLD_MAP_UI.click(mouseX, mouseY, button, worldMapOverlay(), view)) return true;
        if (view == null || !WorldRuntimeState.current().navigationEnabled()) return false;
        WorldMapInputRouting.MousePress route = WorldMapInputRouting.route(
                worldMapOverlay(), mouseX, mouseY, button, view);
        route.selection().ifPresent(SELECTED::set);
        return route.consume();
    }

    public static boolean onWorldMapChar(char character, int modifiers) {
        return WORLD_MAP_UI.character(character);
    }

    public static boolean onWorldMapKey(int keyCode) { return WORLD_MAP_UI.key(keyCode); }
    public static boolean onWorldMapScroll(double vertical) { return WORLD_MAP_UI.scroll(vertical); }
    public static java.util.Optional<MapPoint> consumeWorldMapLocation() { return WORLD_MAP_UI.consumeLocation(); }
    public static boolean consumeFitWorldRequest() { return WORLD_MAP_UI.consumeFitRequest(); }

    public static WorldMapOverlayRenderer.View fitWorldBorder(WorldMapOverlayRenderer.View current) {
        return worldMapOverlay().fitWorldBorder(current);
    }

    public static boolean fullWorldMode() {
        return fullWorldMode;
    }

    public static void enterFullWorldMode(double scale) {
        if (!Double.isFinite(scale) || scale <= 0) return;
        fullWorldFloor = Math.max(1.0e-6, scale);
        fullWorldMode = true;
    }

    public static void leaveFullWorldMode() {
        fullWorldMode = false;
        fullWorldFloor = 0.0625;
    }

    public static double worldMapZoomFloor(double original) {
        WorldRuntimeState state = WorldRuntimeState.current();
        if (!fullWorldMode || !state.extendedZoomEnabled() || state.zoom() == null) return original;
        return Math.min(original, state.zoom().floor(true, fullWorldFloor));
    }

    public static void clearWorldMapScreenState() {
        SELECTED.set(null);
        worldMapOverlay().clearScreenState();
        WORLD_MAP_UI.clearScreenState();
        leaveFullWorldMode();
    }

    private static void drawTile(DrawContext context, WorldMapRenderSession.TileDraw tile) {
        if (!(tile.texture().handle() instanceof Identifier identifier)) return;
        TextureRenderPlan.fullTile(tile.left(), tile.top(), tile.right(), tile.bottom(),
                        tile.texture().u0(), tile.texture().u1(),
                        tile.texture().v0(), tile.texture().v1())
                .ifPresent(quad -> drawTileQuad(context, identifier, quad));
    }

    private static void drawTileClipped(DrawContext context, WorldMapRenderSession.TileDraw tile,
                                        MinimapOverlayRenderer.Clip clip) {
        if (!(tile.texture().handle() instanceof Identifier identifier)) return;
        double originalLeft = Math.min(tile.left(), tile.right());
        double originalRight = Math.max(tile.left(), tile.right());
        double originalTop = Math.min(tile.top(), tile.bottom());
        double originalBottom = Math.max(tile.top(), tile.bottom());
        if (originalRight <= originalLeft || originalBottom <= originalTop) return;
        int firstY = Math.max(clip.top(), (int) Math.floor(originalTop));
        int lastY = Math.min(clip.bottom(), (int) Math.ceil(originalBottom));
        int bandHeight = clip.circular() ? 1 : Math.max(0, lastY - firstY);
        for (int y = firstY; y < lastY; y += Math.max(1, bandHeight)) {
            int bottom = Math.min(lastY, y + Math.max(1, bandHeight));
            double left = Math.max(clip.left(), originalLeft);
            double right = Math.min(clip.right(), originalRight);
            if (clip.circular()) {
                double radius = clip.size() / 2.0;
                double dy = (y + 0.5) - (clip.top() + radius);
                double half = Math.sqrt(Math.max(0, radius * radius - dy * dy));
                left = Math.max(left, clip.left() + radius - half);
                right = Math.min(right, clip.left() + radius + half);
            }
            TextureRenderPlan.clippedTile(
                            originalLeft, originalTop, originalRight, originalBottom,
                            left, y, right, bottom,
                            tile.texture().u0(), tile.texture().u1(),
                            tile.texture().v0(), tile.texture().v1())
                    .ifPresent(quad -> drawTileQuad(context, identifier, quad));
        }
    }

    private static void drawTileQuad(DrawContext context, Identifier identifier, TextureRenderPlan.Quad quad) {
        context.drawTexturedQuad(identifier,
                quad.x1(), quad.y1(), quad.x2(), quad.y2(),
                quad.u1(), quad.u2(), quad.v1(), quad.v2());
    }

    private static void drawCommands(DrawContext context, List<LayerRenderer.DrawCommand> commands,
                                     WorldMapRenderSession.IconLookup icons) {
        drawCommands(context, commands, icons, null);
    }

    private static void drawCommands(DrawContext context, List<LayerRenderer.DrawCommand> commands,
                                     WorldMapRenderSession.IconLookup icons,
                                     MinimapOverlayRenderer.Clip clip) {
        ColoredCommandRenderPlanner.Plan plan = COLORED_COMMANDS.plan(commands,
                context.getScaledWindowWidth(), context.getScaledWindowHeight(), clip);
        for (ColoredCommandRenderPlanner.Element element : plan.elements()) {
            if (element instanceof ColoredCommandRenderPlanner.ColoredBatch batch) {
                context.state.addSimpleElement(new BatchedColoredQuadGuiElementRenderState(
                        context.getMatrices(), batch.quads(), context.scissorStack.peekLast()));
            } else if (element instanceof ColoredCommandRenderPlanner.Icon plannedIcon) {
                LayerRenderer.IconCommand icon = plannedIcon.command();
                TextureRenderPlan.IconRaster raster = TextureRenderPlan.icon(
                        icon.left(), icon.top(), icon.right(), icon.bottom()).orElse(null);
                if (raster == null) continue;
                Identifier texture = icons.resolve(icon.icon()).filter(Identifier.class::isInstance)
                        .map(Identifier.class::cast).orElse(GENERIC_ICON);
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                        raster.x(), raster.y(), raster.sourceX(), raster.sourceY(),
                        raster.width(), raster.height(), raster.sourceWidth(), raster.sourceHeight(),
                        raster.textureWidth(), raster.textureHeight());
            }
        }
    }
}
