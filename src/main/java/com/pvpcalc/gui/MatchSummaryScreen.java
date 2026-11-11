package com.pvpcalc.gui;

import com.pvpcalc.core.Lang;
import com.pvpcalc.core.MatchData;
import com.pvpcalc.core.PlayerData;
import com.pvpcalc.core.Report;
import com.pvpcalc.util.Chat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Big, organised match summary with one expandable block per player. */
public class MatchSummaryScreen extends Screen {
    private final MatchData m;
    private final Set<String> expanded = new HashSet<>();
    private int scroll;

    public MatchSummaryScreen(MatchData m) {
        super(Text.literal(Lang.t("rep.title", m == null ? 0 : m.id)));
        this.m = m;
    }

    private int visibleLines() {
        return Math.max(3, (this.height - 80) / 10);
    }

    @Override
    protected void init() {
        this.clearChildren();
        int y = 8;
        int x = 10;
        for (PlayerData p : m.players.values()) {
            if (x > this.width - 260) break;
            final String uuid = p.uuid;
            String label = (expanded.contains(uuid) ? "\u25bc " : "\u25b6 ") + p.name;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                if (!expanded.remove(uuid)) expanded.add(uuid);
                this.init();
            }).dimensions(x, y, 100, 20).build());
            x += 104;
        }
        int right = this.width - 10;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(Lang.t("btn.copy")), b -> {
            if (this.client != null) this.client.keyboard.setClipboard(Report.plain(Report.full(m, null)));
            Chat.reply("msg.copied");
        }).dimensions(right - 160, y, 75, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal(Lang.t("btn.close")), b -> this.close())
                .dimensions(right - 80, y, 75, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("\u25b2"), b -> scroll(-5)).dimensions(right - 20, 40, 20, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("\u25bc"), b -> scroll(5)).dimensions(right - 20, 64, 20, 20).build());
    }

    private void scroll(int d) {
        List<String> l = Report.full(m, expanded);
        int max = Math.max(0, l.size() - visibleLines());
        scroll = Math.max(0, Math.min(max, scroll + d));
    }

    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll(v > 0 ? -3 : 3);
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        List<String> lines = Report.full(m, expanded);
        int y = 38;
        int end = Math.min(lines.size(), scroll + visibleLines());
        for (int i = scroll; i < end; i++) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(lines.get(i)), 12, y, 0xFFFFFFFF);
            y += 10;
        }
        if (lines.size() > visibleLines()) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal((scroll + 1) + "-" + end + " / " + lines.size()), 12, this.height - 14, 0xFFAAAAAA);
        }
    }
}
