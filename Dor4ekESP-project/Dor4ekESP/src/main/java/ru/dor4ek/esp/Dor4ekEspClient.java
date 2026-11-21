package ru.dor4ek.esp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Properties;

public class Dor4ekEspClient implements ClientModInitializer {
    private static final Config CONFIG = new Config();
    private static final Map<Entity, Boolean> ORIGINAL_GLOW = new WeakHashMap<>();

    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        CONFIG.load();

        KeyBinding.Category category = KeyBinding.Category.create(
                Identifier.of("dor4ek_esp", "controls")
        );

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dor4ek_esp.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_0,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new EspMenuScreen());
                }
            }
            updateGlow(client);
        });
    }

    private static void updateGlow(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            restoreAll();
            return;
        }

        for (Entity entity : client.world.getEntities()) {
            boolean target = isEnabledTarget(entity, client.player);

            if (target) {
                ORIGINAL_GLOW.putIfAbsent(entity, entity.isGlowing());
                entity.setGlowing(true);
            } else {
                restore(entity);
            }
        }

        // Restore entities that have left the loaded entity list.
        ORIGINAL_GLOW.keySet().removeIf(entity -> {
            if (entity == null || entity.getWorld() != client.world) {
                if (entity != null) {
                    Boolean old = ORIGINAL_GLOW.get(entity);
                    if (old != null) entity.setGlowing(old);
                }
                return true;
            }
            return false;
        });
    }

    private static boolean isEnabledTarget(Entity entity, PlayerEntity localPlayer) {
        if (entity == localPlayer || entity == null) return false;

        if (entity instanceof PlayerEntity) return CONFIG.players;
        if (entity instanceof AnimalEntity) return CONFIG.animals;
        if (entity instanceof MobEntity) return CONFIG.mobs;

        return false;
    }

    private static void restore(Entity entity) {
        Boolean old = ORIGINAL_GLOW.remove(entity);
        if (old != null) {
            entity.setGlowing(old);
        }
    }

    private static void restoreAll() {
        for (Map.Entry<Entity, Boolean> entry : ORIGINAL_GLOW.entrySet()) {
            Entity entity = entry.getKey();
            if (entity != null) entity.setGlowing(entry.getValue());
        }
        ORIGINAL_GLOW.clear();
    }

    private static final class Config {
        boolean players = true;
        boolean mobs = true;
        boolean animals = true;

        boolean allEnabled() {
            return players && mobs && animals;
        }

        void setAll(boolean value) {
            players = value;
            mobs = value;
            animals = value;
            save();
        }

        void load() {
            Path file = MinecraftClient.getInstance().runDirectory.toPath()
                    .resolve("config").resolve("dor4ek-esp.properties");
            if (!Files.exists(file)) return;

            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
                players = Boolean.parseBoolean(p.getProperty("players", "true"));
                mobs = Boolean.parseBoolean(p.getProperty("mobs", "true"));
                animals = Boolean.parseBoolean(p.getProperty("animals", "true"));
            } catch (IOException ignored) {
            }
        }

        void save() {
            Path file = MinecraftClient.getInstance().runDirectory.toPath()
                    .resolve("config").resolve("dor4ek-esp.properties");
            try {
                Files.createDirectories(file.getParent());
                Properties p = new Properties();
                p.setProperty("players", Boolean.toString(players));
                p.setProperty("mobs", Boolean.toString(mobs));
                p.setProperty("animals", Boolean.toString(animals));
                try (OutputStream out = Files.newOutputStream(file)) {
                    p.store(out, "Dor4ek ESP settings");
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static final class EspMenuScreen extends Screen {
        private static final int PANEL = 0xE9161D2B;
        private static final int ACCENT = 0xFF45D6C5;
        private static final int MUTED = 0xFF9AA9BC;

        EspMenuScreen() {
            super(Text.literal("Dor4ek ESP"));
        }

        @Override
        protected void init() {
            int buttonWidth = 190;
            int x = (this.width - buttonWidth) / 2;
            int y = this.height / 2 - 35;

            addDrawableChild(toggleButton(x, y, "Players", () -> CONFIG.players,
                    () -> CONFIG.players = !CONFIG.players));
            addDrawableChild(toggleButton(x, y + 27, "Mobs", () -> CONFIG.mobs,
                    () -> CONFIG.mobs = !CONFIG.mobs));
            addDrawableChild(toggleButton(x, y + 54, "Animals", () -> CONFIG.animals,
                    () -> CONFIG.animals = !CONFIG.animals));
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("ALL — включить всё"),
                    button -> CONFIG.setAll(true)
            ).dimensions(x, y + 88, buttonWidth, 20).build());
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Выключить всё"),
                    button -> CONFIG.setAll(false)
            ).dimensions(x, y + 112, buttonWidth, 20).build());
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Готово"),
                    button -> close()
            ).dimensions(x, y + 143, buttonWidth, 20).build());
        }

        private ButtonWidget toggleButton(int x, int y, String name,
                                          java.util.function.BooleanSupplier enabled,
                                          Runnable toggle) {
            return ButtonWidget.builder(
                    Text.literal(name + ": " + (enabled.getAsBoolean() ? "ON" : "OFF")),
                    button -> {
                        toggle.run();
                        CONFIG.save();
                        if (client != null) client.setScreen(new EspMenuScreen());
                    }
            ).dimensions(x, y, 190, 20).build();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);

            int panelWidth = 250;
            int panelHeight = 225;
            int left = (width - panelWidth) / 2;
            int top = (height - panelHeight) / 2;

            context.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
            context.fill(left, top, left + panelWidth, top + 3, ACCENT);

            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("DOR4EK ESP"), width / 2, top + 13, ACCENT);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Entity highlighting"), width / 2, top + 29, MUTED);

            super.render(context, mouseX, mouseY, delta);

            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Открыть меню: клавиша 0"),
                    width / 2, top + panelHeight - 17, MUTED);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
