package com.pvpcalc.gui;

import com.pvpcalc.config.ConfigIo;
import com.pvpcalc.core.Lang;
import com.pvpcalc.core.SettingText;
import com.pvpcalc.core.Settings;
import com.pvpcalc.input.KeyBindings;
import com.pvpcalc.util.Chat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Litematica-style settings screen: tabs, search, a list with one widget per option, a reset button per option,
 * Reset tab / Export / Import / Done. Built only from vanilla widgets. Every callback is wrapped in try/catch.
 */
public class SettingsScreen extends Screen {
    private static int tabIdx = 0;
    private static String search = "";

    private final Screen parent;
    private int page;
    private final List<ClickableWidget> rowWidgets = new ArrayList<>();
    private final List<Settings.Def> pageRows = new ArrayList<>();
    private final List<ButtonWidget> tabButtons = new ArrayList<>();
    private ButtonWidget resetTabButton;
    private long confirmUntil;
    private String message = "";
    private int rowsTop = 54;

    // --- hotkey editing (click a key button, press the new key; Esc clears it) ---
    private static final int[] KEY_CODES = buildKeyCodes();
    private final List<KeyBinding> pageKeys = new ArrayList<>();
    private KeyBinding listening;
    private final Set<Integer> held = new HashSet<>();

    private static int[] buildKeyCodes() {
        List<Integer> l = new ArrayList<>();
        l.add(32); l.add(39);
        for (int i = 44; i <= 57; i++) l.add(i);
        l.add(59); l.add(61);
        for (int i = 65; i <= 93; i++) l.add(i);
        l.add(96); l.add(161); l.add(162);
        for (int i = 256; i <= 269; i++) l.add(i);
        for (int i = 280; i <= 284; i++) l.add(i);
        for (int i = 290; i <= 314; i++) l.add(i);
        for (int i = 320; i <= 348; i++) l.add(i);
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    public SettingsScreen(Screen parent) {
        super(Text.literal(Lang.t("gui.title")));
        this.parent = parent;
    }

    // ---------------- naming ----------------

    static String nameOf(Settings.Def d) {
        return SettingText.name(d);
    }

    static String descOf(Settings.Def d) {
        return SettingText.desc(d);
    }

    private List<Settings.Def> visible() {
        List<Settings.Def> out = new ArrayList<>();
        String q = search.trim().toLowerCase(Locale.ROOT);
        for (Settings.Def d : Settings.ALL) {
            if (d.hidden) continue;
            if (q.isEmpty()) {
                if (d.tab.equals(Settings.TABS[tabIdx])) out.add(d);
            } else if (nameOf(d).toLowerCase(Locale.ROOT).contains(q) || d.id.toLowerCase(Locale.ROOT).contains(q)
                    || descOf(d).toLowerCase(Locale.ROOT).contains(q)) {
                out.add(d);
            }
        }
        return out;
    }

    private int perPage() {
        return Math.max(1, (this.height - rowsTop - 40) / 22);
    }

    // ---------------- layout ----------------

    @Override
    protected void init() {
        this.clearChildren();
        rowWidgets.clear();
        tabButtons.clear();

        TextFieldWidget box = new TextFieldWidget(this.textRenderer, this.width - 172, 6, 160, 16, Text.literal("search"));
        box.setText(search);
        box.setChangedListener(s -> guard(() -> { search = s; page = 0; buildRows(); }));
        this.addDrawableChild(box);

        int n = Settings.TABS.length;
        int tw = Math.max(40, (this.width - 16) / n);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            ButtonWidget b = ButtonWidget.builder(Text.literal(Lang.t("tab." + Settings.TABS[i])), btn -> guard(() -> {
                tabIdx = idx; search = ""; box.setText(""); page = 0; refreshTabs(); buildRows();
            })).dimensions(8 + i * tw, 28, tw - 2, 20).build();
            tabButtons.add(b);
            this.addDrawableChild(b);
        }
        refreshTabs();

        int by = this.height - 28;
        int bw = Math.max(50, (this.width - 20) / 6);
        resetTabButton = ButtonWidget.builder(Text.literal(Lang.t("gui.reset_tab")), b -> guard(this::onResetTab))
                .dimensions(8, by, bw - 2, 20).build();
        this.addDrawableChild(resetTabButton);
        this.addDrawableChild(ButtonWidget.builder(Text.literal(Lang.t("gui.export")), b -> guard(() -> {
            Path p = ConfigIo.export("export");
            message = p == null ? Lang.t("err.write_failed") : Lang.t("gui.exported", p.getFileName());
        })).dimensions(8 + bw, by, bw - 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal(Lang.t("gui.import")), b -> guard(() -> {
            String err = ConfigIo.importFile("export");
            message = err == null ? Lang.t("gui.imported") : Lang.t(err);
            buildRows();
        })).dimensions(8 + 2 * bw, by, bw - 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("\u25c0"), b -> guard(() -> { page = Math.max(0, page - 1); buildRows(); }))
                .dimensions(8 + 3 * bw, by, bw - 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("\u25b6"), b -> guard(() -> { page++; buildRows(); }))
                .dimensions(8 + 4 * bw, by, bw - 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal(Lang.t("gui.done")), b -> this.close())
                .dimensions(8 + 5 * bw, by, bw - 2, 20).build());
        buildRows();
    }

    private void refreshTabs() {
        for (int i = 0; i < tabButtons.size(); i++) tabButtons.get(i).active = (i != tabIdx) || !search.isEmpty();
    }

    private void onResetTab() {
        long now = System.currentTimeMillis();
        if (now > confirmUntil) {
            confirmUntil = now + 3000;
            resetTabButton.setMessage(Text.literal(Lang.t("gui.confirm")));
            return;
        }
        confirmUntil = 0;
        resetTabButton.setMessage(Text.literal(Lang.t("gui.reset_tab")));
        Settings.resetTab(Settings.TABS[tabIdx]);
        buildRows();
    }

    private void guard(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            message = "Error: " + t.getClass().getSimpleName();
            com.pvpcalc.PvPCalcClient.LOGGER.error("Settings screen error", t);
        }
    }

    private void buildRows() {
        for (ClickableWidget w : rowWidgets) this.remove(w);
        rowWidgets.clear();
        pageRows.clear();
        pageKeys.clear();
        if (Settings.TABS[tabIdx].equals("hotkeys") && search.isBlank()) {
            addKeyRows();
            return;
        }
        List<Settings.Def> all = visible();
        int per = perPage();
        int maxPage = Math.max(0, (all.size() - 1) / per);
        page = Math.max(0, Math.min(page, maxPage));
        int y = rowsTop;
        for (int i = page * per; i < Math.min(all.size(), (page + 1) * per); i++) {
            Settings.Def d = all.get(i);
            pageRows.add(d);
            addRow(d, y);
            y += 22;
        }
    }

    private <T extends ClickableWidget> T put(T w) {
        rowWidgets.add(w);
        this.addDrawableChild(w);
        return w;
    }

    private static String shown(Settings.Def d) {
        if (d.type == Settings.Type.BOOL) return Settings.bool(d.id) ? "\u00a7aON" : "\u00a7cOFF";
        String s = Settings.str(d.id);
        return s.isEmpty() ? "NONE" : s;
    }

    private void addRow(Settings.Def d, int y) {
        int vx = this.width / 2 + 10, vw = 140;
        Tooltip tip = Tooltip.of(Text.literal(descOf(d)));
        final String id = d.id;
        switch (d.type) {
            case BOOL:
            case ENUM: {
                ButtonWidget b = ButtonWidget.builder(Text.literal(shown(d)), btn -> guard(() -> {
                    Settings.cycle(id, Screen.hasShiftDown() ? -1 : 1);
                    btn.setMessage(Text.literal(shown(d)));
                })).dimensions(vx, y, vw, 20).build();
                b.setTooltip(tip);
                put(b);
                break;
            }
            default: {
                TextFieldWidget f = new TextFieldWidget(this.textRenderer, vx, y, vw, 20, Text.literal(nameOf(d)));
                f.setMaxLength(120);
                f.setText(Settings.str(id));
                f.setChangedListener(s -> guard(() -> {
                    String err = Settings.set(id, s);
                    f.setEditableColor(err == null ? 0xFFFFFFFF : 0xFFFF5555);   // red until the value is valid
                }));
                f.setTooltip(tip);
                put(f);
            }
        }
        ButtonWidget reset = ButtonWidget.builder(Text.literal("\u21ba"), b -> guard(() -> { Settings.reset(id); buildRows(); }))
                .dimensions(vx + vw + 4, y, 20, 20).build();
        reset.setTooltip(Tooltip.of(Text.literal(Lang.t("gui.reset_one", Settings.showDefault(id)))));
        put(reset);
    }

    private static String keyName(KeyBinding kb) {
        return I18n.translate(kb.getTranslationKey());
    }

    private String conflictOf(KeyBinding kb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null || kb.isUnbound()) return null;
        for (KeyBinding o : mc.options.allKeys) {
            if (o != kb && o.getBoundKeyTranslationKey().equals(kb.getBoundKeyTranslationKey())) return keyName(o);
        }
        return null;
    }

    private String keyLabel(KeyBinding kb) {
        if (listening == kb) return "> " + Lang.t("gui.hotkey_press") + " <";
        return (conflictOf(kb) != null ? "\u00a7c" : "") + kb.getBoundKeyLocalizedText().getString();
    }

    private void addKeyRows() {
        List<KeyBinding> all = KeyBindings.all();
        int per = perPage();
        int maxPage = Math.max(0, (all.size() - 1) / per);
        page = Math.max(0, Math.min(page, maxPage));
        int vx = this.width / 2 + 10, vw = 140, y = rowsTop;
        for (int i = page * per; i < Math.min(all.size(), (page + 1) * per); i++) {
            final KeyBinding kb = all.get(i);
            pageKeys.add(kb);
            ButtonWidget b = ButtonWidget.builder(Text.literal(keyLabel(kb)), btn -> guard(() -> startListening(kb)))
                    .dimensions(vx, y, vw, 20).build();
            b.setTooltip(Tooltip.of(Text.literal(Lang.t("gui.hotkey_hint"))));
            put(b);
            ButtonWidget reset = ButtonWidget.builder(Text.literal("\u21ba"), btn -> guard(() -> {
                assign(kb, kb.getDefaultKey());
                buildRows();
            })).dimensions(vx + vw + 4, y, 20, 20).build();
            reset.setTooltip(Tooltip.of(Text.literal(Lang.t("gui.reset_one", kb.getDefaultKey().getLocalizedText().getString()))));
            put(reset);
            y += 22;
        }
    }

    private void startListening(KeyBinding kb) {
        listening = kb;
        held.clear();
        long h = GLFW.glfwGetCurrentContext();
        if (h != 0L) for (int code : KEY_CODES) if (GLFW.glfwGetKey(h, code) == GLFW.GLFW_PRESS) held.add(code);
        buildRows();
    }

    private void assign(KeyBinding kb, InputUtil.Key key) {
        MinecraftClient mc = MinecraftClient.getInstance();
        kb.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        if (mc.options != null) mc.options.write();
        message = Lang.t("msg.set", keyName(kb), kb.getBoundKeyLocalizedText().getString());
    }

    /** Looks at the keyboard while a key button is waiting for the new key. Escape clears the binding. */
    private void pollKey() {
        long h = GLFW.glfwGetCurrentContext();
        if (h == 0L || listening == null) return;
        for (int code : KEY_CODES) {
            if (GLFW.glfwGetKey(h, code) != GLFW.GLFW_PRESS) {
                held.remove(code);
                continue;
            }
            if (held.contains(code)) continue;          // was already down when the button was clicked
            KeyBinding kb = listening;
            listening = null;
            if (code == GLFW.GLFW_KEY_ESCAPE) assign(kb, InputUtil.UNKNOWN_KEY);
            else assign(kb, InputUtil.Type.KEYSYM.createFromCode(code));
            buildRows();
            return;
        }
    }

    /** While waiting for a key, Escape must not close the screen. */
    public boolean shouldCloseOnEsc() {
        return listening == null;
    }

    public boolean mouseScrolled(double mx, double my, double h, double v) {
        guard(() -> { page = Math.max(0, page + (v > 0 ? -1 : 1)); buildRows(); });
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (listening != null) guard(this::pollKey);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(Lang.t("gui.title")), 10, 11, 0xFFFFFF55);
        int y = rowsTop;
        for (Settings.Def d : pageRows) {
            int color = Settings.isDefault(d.id) ? 0xFFFFFFFF : 0xFFFFD855;   // highlighted when changed
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(nameOf(d)), 16, y + 6, color);
            y += 22;
        }
        int ky = rowsTop;
        for (KeyBinding kb : pageKeys) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(keyName(kb)), 16, ky + 6, 0xFFFFFFFF);
            String c = conflictOf(kb);
            if (c != null) ctx.drawTextWithShadow(this.textRenderer, Text.literal(Lang.t("gui.hotkey_conflict", c)), this.width / 2 + 178, ky + 6, 0xFFFF5555);
            ky += 22;
        }
        if (!pageKeys.isEmpty()) ctx.drawTextWithShadow(this.textRenderer, Text.literal(Lang.t("gui.hotkeys_info")), 16, this.height - 52, 0xFFAAAAAA);
        if (!message.isEmpty()) ctx.drawTextWithShadow(this.textRenderer, Text.literal(message), 10, this.height - 40, 0xFF55FF55);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(Lang.t("gui.page", page + 1)), this.width - 60, this.height - 40, 0xFFAAAAAA);
        if (System.currentTimeMillis() > confirmUntil && confirmUntil != 0) {
            confirmUntil = 0;
            resetTabButton.setMessage(Text.literal(Lang.t("gui.reset_tab")));
        }
    }

    @Override
    public void removed() {
        ConfigIo.saveIfDirty();   // saved when the screen closes (and after every command change in the tick loop)
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
