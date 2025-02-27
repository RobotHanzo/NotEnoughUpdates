package io.github.moulberry.notenoughupdates.options;

import com.google.common.collect.Lists;
import io.github.moulberry.notenoughupdates.core.GlScissorStack;
import io.github.moulberry.notenoughupdates.core.GuiElement;
import io.github.moulberry.notenoughupdates.core.config.Config;
import io.github.moulberry.notenoughupdates.core.config.gui.GuiOptionEditor;
import io.github.moulberry.notenoughupdates.core.config.struct.ConfigProcessor;
import io.github.moulberry.notenoughupdates.core.util.lerp.LerpUtils;
import io.github.moulberry.notenoughupdates.core.util.lerp.LerpingInteger;
import io.github.moulberry.notenoughupdates.core.util.render.RenderUtils;
import io.github.moulberry.notenoughupdates.core.util.render.TextRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import static io.github.moulberry.notenoughupdates.util.GuiTextures.*;

import java.awt.*;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NEUConfigEditor extends GuiElement {

    private static final ResourceLocation[] socialsIco = new ResourceLocation[] {
            DISCORD,
            GITHUB,
            TWITTER,
            YOUTUBE,
            PATREON
    };
    private static final String[] socialsLink = new String[] {
            "https://discord.gg/moulberry",
            "https://github.com/Moulberry/Hychat",
            "https://twitter.com/moulberry/",
            "https://www.youtube.com/channel/UCPh-OKmRSS3IQi9p6YppLcw",
            "https://patreon.com/moulberry"
    };

    private final long openedMillis;

    private String selectedCategory = null;

    private LerpingInteger optionsScroll = new LerpingInteger(0, 150);

    private LinkedHashMap<String, ConfigProcessor.ProcessedCategory> processedConfig;

    public NEUConfigEditor(Config config) {
        this.openedMillis = System.currentTimeMillis();
        this.processedConfig = ConfigProcessor.create(config);
    }

    private LinkedHashMap<String, ConfigProcessor.ProcessedCategory> getCurrentConfigEditing() {
        return processedConfig;
    }

    private String getSelectedCategory() {
        return selectedCategory;
    }

    private void setSelectedCategory(String category) {
        selectedCategory = category;
        optionsScroll.setValue(0);
    }

    public void render() {
        optionsScroll.tick();

        List<String> tooltipToDisplay = null;

        long currentTime = System.currentTimeMillis();
        long delta = currentTime - openedMillis;

        ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());
        int width = scaledResolution.getScaledWidth();
        int height = scaledResolution.getScaledHeight();
        int mouseX = Mouse.getX() * width / Minecraft.getMinecraft().displayWidth;
        int mouseY = height - Mouse.getY() * height / Minecraft.getMinecraft().displayHeight - 1;

        float opacityFactor = LerpUtils.sigmoidZeroOne(delta/500f);
        RenderUtils.drawGradientRect(0, 0, 0, width, height,
                (int)(0x80*opacityFactor) << 24 | 0x101010,
                (int)(0x90*opacityFactor) << 24 | 0x101010);

        int xSize = Math.min(scaledResolution.getScaledWidth()-100/scaledResolution.getScaleFactor(), 500);
        int ySize = Math.min(scaledResolution.getScaledHeight()-100/scaledResolution.getScaleFactor(), 400);

        int x = (scaledResolution.getScaledWidth() - xSize)/2;
        int y = (scaledResolution.getScaledHeight() - ySize)/2;

        int adjScaleFactor = Math.max(2, scaledResolution.getScaleFactor());

        int openingXSize = xSize;
        int openingYSize = ySize;
        if(delta < 150) {
            openingXSize = (int)(delta*xSize/150);
            openingYSize = 5;
        } else if(delta < 300) {
            openingYSize = 5 + (int)(delta-150)*(ySize-5)/150;
        }
        RenderUtils.drawFloatingRectDark(
                (scaledResolution.getScaledWidth() - openingXSize)/2,
                (scaledResolution.getScaledHeight() - openingYSize)/2,
                openingXSize, openingYSize);
        GlScissorStack.clear();
        GlScissorStack.push((scaledResolution.getScaledWidth() - openingXSize)/2,
                (scaledResolution.getScaledHeight() - openingYSize)/2,
                (scaledResolution.getScaledWidth() + openingXSize)/2,
                (scaledResolution.getScaledHeight() + openingYSize)/2, scaledResolution);

        RenderUtils.drawFloatingRectDark(x+5, y+5, xSize-10, 20, false);

        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        TextRenderUtils.drawStringCenteredScaledMaxWidth("NotEnoughUpdates by "+EnumChatFormatting.DARK_PURPLE+"Moulberry",
                fr, x+xSize/2, y+15, false, 200, 0xa0a0a0);

        RenderUtils.drawFloatingRectDark(x+4, y+49-20,
                140, ySize-54+20, false);

        int innerPadding = 20/adjScaleFactor;
        int innerLeft = x+4+innerPadding;
        int innerRight = x+144-innerPadding;
        int innerTop = y+49+innerPadding;
        int innerBottom = y+ySize-5-innerPadding;
        Gui.drawRect(innerLeft, innerTop, innerLeft+1, innerBottom, 0xff08080E); //Left
        Gui.drawRect(innerLeft+1, innerTop, innerRight, innerTop+1, 0xff08080E); //Top
        Gui.drawRect(innerRight-1, innerTop+1, innerRight, innerBottom, 0xff28282E); //Right
        Gui.drawRect(innerLeft+1, innerBottom-1, innerRight-1, innerBottom, 0xff28282E); //Bottom
        Gui.drawRect(innerLeft+1, innerTop+1, innerRight-1, innerBottom-1, 0x6008080E); //Middle

        GlScissorStack.push(0, innerTop+1, scaledResolution.getScaledWidth(),
                innerBottom-1, scaledResolution);

        int categoryIndex = 0;
        for(Map.Entry<String, ConfigProcessor.ProcessedCategory> entry : getCurrentConfigEditing().entrySet()) {
            if(getSelectedCategory() == null) {
                setSelectedCategory(entry.getKey());
            }
            String catName = entry.getValue().name;
            if(entry.getKey().equals(getSelectedCategory())) {
                catName = EnumChatFormatting.DARK_AQUA.toString() + EnumChatFormatting.UNDERLINE + catName;
            } else {
                catName = EnumChatFormatting.GRAY + catName;
            }
            TextRenderUtils.drawStringCenteredScaledMaxWidth(catName,
                    fr, x+75, y+70+categoryIndex*15, false, 140, -1);
            categoryIndex++;
        }

        GlScissorStack.pop(scaledResolution);

        TextRenderUtils.drawStringCenteredScaledMaxWidth("Categories",
                fr, x+75, y+44, false, 120, 0xa368ef);

        RenderUtils.drawFloatingRectDark(x+149, y+29,
                xSize-154, ySize-34, false);

        if(getSelectedCategory() != null && getCurrentConfigEditing().containsKey(getSelectedCategory())) {
            ConfigProcessor.ProcessedCategory cat = getCurrentConfigEditing().get(getSelectedCategory());

            TextRenderUtils.drawStringCenteredScaledMaxWidth(cat.desc,
                    fr, x+xSize/2+72, y+44, true, xSize-154-innerPadding*2, 0xb0b0b0);
        }

        innerLeft = x+149+innerPadding;
        innerRight =x+xSize-5-innerPadding;
        //innerTop = y+29+innerPadding;
        innerBottom = y+ySize-5-innerPadding;
        Gui.drawRect(innerLeft, innerTop, innerLeft+1, innerBottom, 0xff08080E); //Left
        Gui.drawRect(innerLeft+1, innerTop, innerRight, innerTop+1, 0xff08080E); //Top
        Gui.drawRect(innerRight-1, innerTop+1, innerRight, innerBottom, 0xff303036); //Right
        Gui.drawRect(innerLeft+1, innerBottom-1, innerRight-1, innerBottom, 0xff303036); //Bottom
        Gui.drawRect(innerLeft+1, innerTop+1, innerRight-1, innerBottom-1, 0x6008080E); //Middle

        GlScissorStack.push(innerLeft+1, innerTop+1, innerRight-1, innerBottom-1, scaledResolution);

        float barSize = 1;
        int optionY = -optionsScroll.getValue();
        if(getSelectedCategory() != null && getCurrentConfigEditing().containsKey(getSelectedCategory())) {
            ConfigProcessor.ProcessedCategory cat = getCurrentConfigEditing().get(getSelectedCategory());
            int optionWidth = innerRight-innerLeft-20;
            GlStateManager.enableDepth();
            for(ConfigProcessor.ProcessedOption option : cat.options.values()) {
                GuiOptionEditor editor = option.editor;
                if(editor == null) {
                    continue;
                }
                int optionHeight = editor.getHeight();
                if(innerTop+5+optionY+optionHeight > innerTop+1 && innerTop+5+optionY < innerBottom-1) {
                    editor.render(innerLeft+5, innerTop+5+optionY, optionWidth);
                }
                optionY += optionHeight + 5;
            }
            GlStateManager.disableDepth();
            if(optionY > 0) {
                barSize = LerpUtils.clampZeroOne((float)(innerBottom-innerTop-2)/(optionY+5+optionsScroll.getValue()));
            }
        }

        GlScissorStack.pop(scaledResolution);

        float barStart = optionsScroll.getValue() / (float)(optionY + optionsScroll.getValue());
        float barEnd = barStart+barSize;
        if(barEnd > 1) {
            barEnd = 1;
            if(optionsScroll.getTarget()/(float)(optionY + optionsScroll.getValue())+barSize < 1) {
                int target = optionsScroll.getTarget();
                optionsScroll.setValue((int)Math.ceil((optionY+5+optionsScroll.getValue())-barSize*(optionY+5+optionsScroll.getValue())));
                optionsScroll.setTarget(target);
            } else {
                optionsScroll.setValue((int)Math.ceil((optionY+5+optionsScroll.getValue())-barSize*(optionY+5+optionsScroll.getValue())));
            }
        }
        int dist = innerBottom-innerTop-12;
        Gui.drawRect(innerRight-10, innerTop+5, innerRight-5, innerBottom-5, 0xff101010);
        Gui.drawRect(innerRight-9, innerTop+6+(int)(dist*barStart), innerRight-6, innerTop+6+(int)(dist*barEnd), 0xff303030);

        for(int socialIndex=0; socialIndex<socialsIco.length; socialIndex++) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(socialsIco[socialIndex]);
            GlStateManager.color(1, 1, 1, 1);
            int socialLeft = x+xSize-23-18*socialIndex;
            RenderUtils.drawTexturedRect(socialLeft, y+7, 16, 16, GL11.GL_LINEAR);

            if(mouseX >= socialLeft && mouseX <= socialLeft+16 &&
                mouseY >= y+6 && mouseY <= y+23) {
                tooltipToDisplay = Lists.newArrayList(EnumChatFormatting.YELLOW+"Go to: "+EnumChatFormatting.RESET+socialsLink[socialIndex]);
            }
        }

        GlScissorStack.clear();

        if(tooltipToDisplay != null) {
            TextRenderUtils.drawHoveringText(tooltipToDisplay, mouseX, mouseY, width, height, -1, fr);
        }

        GlStateManager.translate(0, 0, -2);
    }

    public boolean mouseInput(int mouseX, int mouseY) {
        ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());
        int width = scaledResolution.getScaledWidth();
        int height = scaledResolution.getScaledHeight();

        int xSize = Math.min(width-100/scaledResolution.getScaleFactor(), 500);
        int ySize = Math.min(height-100/scaledResolution.getScaleFactor(), 400);

        int x = (scaledResolution.getScaledWidth() - xSize)/2;
        int y = (scaledResolution.getScaledHeight() - ySize)/2;

        int adjScaleFactor = Math.max(2, scaledResolution.getScaleFactor());

        int innerPadding = 20/adjScaleFactor;
        int innerTop = y+49+innerPadding;
        int innerBottom = y+ySize-5-innerPadding;
        int innerLeft = x+149+innerPadding;
        int innerRight = x+xSize-5-innerPadding;

        int dWheel = Mouse.getDWheel();
        if(mouseY > innerTop && mouseY < innerBottom && dWheel != 0) {
            if(dWheel < 0) {
                dWheel = -1;
            }
            if(dWheel > 0) {
                dWheel = 1;
            }
            boolean resetTimer = true;
            int newTarget = optionsScroll.getTarget() - dWheel*30;
            if(newTarget < 0) {
                newTarget = 0;
                resetTimer = false;
            }

            float barSize = 1;
            int optionY = -newTarget;
            if(getSelectedCategory() != null && getCurrentConfigEditing() != null && getCurrentConfigEditing().containsKey(getSelectedCategory())) {
                ConfigProcessor.ProcessedCategory cat = getCurrentConfigEditing().get(getSelectedCategory());
                for(ConfigProcessor.ProcessedOption option : cat.options.values()) {
                    GuiOptionEditor editor = option.editor;
                    if(editor == null) {
                        continue;
                    }
                    optionY += editor.getHeight() + 5;

                    if(optionY > 0) {
                        barSize = LerpUtils.clampZeroOne((float)(innerBottom-innerTop-2)/(optionY+5 + newTarget));
                    }
                }
            }

            int barMax = (int)Math.floor((optionY+5+newTarget)-barSize*(optionY+5+newTarget));
            if(newTarget > barMax) {
                newTarget = barMax;
                resetTimer = false;
            }
            if(resetTimer && optionsScroll.getTarget() != newTarget) {
                optionsScroll.resetTimer();
            }
            optionsScroll.setTarget(newTarget);
        } else if(Mouse.getEventButtonState() && Mouse.getEventButton() == 0) {
            if(getCurrentConfigEditing() != null) {
                int categoryIndex = 0;
                for(Map.Entry<String, ConfigProcessor.ProcessedCategory> entry : getCurrentConfigEditing().entrySet()) {
                    if(getSelectedCategory() == null) {
                        setSelectedCategory(entry.getKey());
                    }
                    if(mouseX >= x+5 && mouseX <= x+145 &&
                            mouseY >= y+70+categoryIndex*15-7 && mouseY <= y+70+categoryIndex*15+7) {
                        setSelectedCategory(entry.getKey());
                        return true;
                    }
                    categoryIndex++;
                }
            }

            for(int socialIndex=0; socialIndex<socialsLink.length; socialIndex++) {
                int socialLeft = x+xSize-23-18*socialIndex;

                if(mouseX >= socialLeft && mouseX <= socialLeft+16 &&
                        mouseY >= y+6 && mouseY <= y+23) {
                    try {
                        Desktop.getDesktop().browse(new URI(socialsLink[socialIndex]));
                    } catch(Exception ignored) {}
                    return true;
                }
            }
        }

        int optionY = -optionsScroll.getValue();
        if(getSelectedCategory() != null && getCurrentConfigEditing() != null && getCurrentConfigEditing().containsKey(getSelectedCategory())) {
            int optionWidth = innerRight-innerLeft-20;
            ConfigProcessor.ProcessedCategory cat = getCurrentConfigEditing().get(getSelectedCategory());
            for(ConfigProcessor.ProcessedOption option : cat.options.values()) {
                GuiOptionEditor editor = option.editor;
                if(editor == null) {
                    continue;
                }
                if(editor.mouseInputGlobal(innerLeft+5, innerTop+5+optionY, optionWidth, mouseX, mouseY)) {
                    return true;
                }
                optionY += editor.getHeight() + 5;
            }
        }

        if(mouseX > innerLeft && mouseX < innerRight &&
                mouseY > innerTop && mouseY < innerBottom) {
            optionY = -optionsScroll.getValue();
            if(getSelectedCategory() != null && getCurrentConfigEditing() != null && getCurrentConfigEditing().containsKey(getSelectedCategory())) {
                int optionWidth = innerRight-innerLeft-20;
                ConfigProcessor.ProcessedCategory cat = getCurrentConfigEditing().get(getSelectedCategory());
                for(ConfigProcessor.ProcessedOption option : cat.options.values()) {
                    GuiOptionEditor editor = option.editor;
                    if(editor == null) {
                        continue;
                    }
                    if(editor.mouseInput(innerLeft+5, innerTop+5+optionY, optionWidth, mouseX, mouseY)) {
                        return true;
                    }
                    optionY += editor.getHeight() + 5;
                }
            }
        }

        return true;
    }

    public boolean keyboardInput() {
        if(getSelectedCategory() != null && getCurrentConfigEditing() != null && getCurrentConfigEditing().containsKey(getSelectedCategory())) {
            ConfigProcessor.ProcessedCategory cat = getCurrentConfigEditing().get(getSelectedCategory());
            for(ConfigProcessor.ProcessedOption option : cat.options.values()) {
                GuiOptionEditor editor = option.editor;
                if(editor == null) {
                    continue;
                }
                if(editor.keyboardInput()) {
                    return true;
                }
            }
        }

        return true;
    }
}
