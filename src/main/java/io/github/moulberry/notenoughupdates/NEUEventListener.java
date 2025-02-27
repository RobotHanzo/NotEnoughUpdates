package io.github.moulberry.notenoughupdates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.moulberry.notenoughupdates.auction.APIManager;
import io.github.moulberry.notenoughupdates.auction.CustomAHGui;
import io.github.moulberry.notenoughupdates.cosmetics.CapeManager;
import io.github.moulberry.notenoughupdates.dungeons.DungeonBlocks;
import io.github.moulberry.notenoughupdates.dungeons.DungeonWin;
import io.github.moulberry.notenoughupdates.gamemodes.SBGamemodes;
import io.github.moulberry.notenoughupdates.miscfeatures.BetterContainers;
import io.github.moulberry.notenoughupdates.miscfeatures.StreamerMode;
import io.github.moulberry.notenoughupdates.miscgui.*;
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer;
import io.github.moulberry.notenoughupdates.util.RequestFocusListener;
import io.github.moulberry.notenoughupdates.util.SBInfo;
import io.github.moulberry.notenoughupdates.util.Constants;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.text.WordUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.moulberry.notenoughupdates.util.GuiTextures.dungeon_chest_worth;

public class NEUEventListener {

    private NotEnoughUpdates neu;

    private boolean hoverInv = false;
    private boolean focusInv = false;

    private boolean joinedSB = false;

    public NEUEventListener(NotEnoughUpdates neu) {
        this.neu = neu;
    }

    private void displayUpdateMessageIfOutOfDate() {
        File repo = neu.manager.repoLocation;
        if(repo.exists()) {
            File updateJson = new File(repo, "update.json");
            try {
                JsonObject o = neu.manager.getJsonFromFile(updateJson);

                String version = o.get("version").getAsString();

                boolean shouldUpdate = !NotEnoughUpdates.VERSION.equalsIgnoreCase(version);
                if(o.has("version_id") && o.get("version_id").isJsonPrimitive()) {
                    int version_id = o.get("version_id").getAsInt();
                    shouldUpdate = version_id > NotEnoughUpdates.VERSION_ID;
                }

                if(shouldUpdate) {
                    String update_msg = o.get("update_msg").getAsString();

                    int first_len = -1;
                    for(String line : update_msg.split("\n")) {
                        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
                        int len = fr.getStringWidth(line);
                        if(first_len == -1) {
                            first_len = len;
                        }
                        int missing_len = first_len-len;
                        if(missing_len > 0) {
                            StringBuilder sb = new StringBuilder(line);
                            for(int i=0; i<missing_len/8; i++) {
                                sb.insert(0, " ");
                            }
                            line = sb.toString();
                        }
                        line = line.replaceAll("\\{version}", version);
                        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(line));
                    }

                    neu.displayLinks(o);

                    Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(""));
                }
            } catch(Exception ignored) {}
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Unload event) {
        NotEnoughUpdates.INSTANCE.saveConfig();
    }

    private long notificationDisplayMillis = 0;
    private List<String> notificationLines = null;

    /**
     * 1)Will send the cached message from #sendChatMessage when at least 200ms has passed since the last message.
     * This is used in order to prevent the mod spamming messages.
     * 2)Adds unique items to the collection log
     */
    private HashMap<String, Long> newItemAddMap = new HashMap<>();
    private long lastLongUpdate = 0;
    private long lastVeryLongUpdate = 0;
    private long lastSkyblockScoreboard = 0;
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.START) return;

        boolean longUpdate = false;
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastLongUpdate > 1000) {
            longUpdate = true;
            lastLongUpdate = currentTime;
        }
        if(!neu.config.dungeonBlock.slowDungeonBlocks) {
            DungeonBlocks.tick();
        }
        DungeonWin.tick();
        if(longUpdate) {
            NotEnoughUpdates.INSTANCE.overlay.redrawItems();

            if(neu.config.dungeonBlock.slowDungeonBlocks) {
                DungeonBlocks.tick();
            }

            neu.updateSkyblockScoreboard();
            CapeManager.getInstance().tick();

            if(Minecraft.getMinecraft().currentScreen instanceof GuiChest) {
                GuiChest eventGui = (GuiChest) Minecraft.getMinecraft().currentScreen;
                ContainerChest cc = (ContainerChest) eventGui.inventorySlots;
                String containerName = cc.getLowerChestInventory().getDisplayName().getUnformattedText();
                if(!containerName.trim().startsWith("Accessory Bag")) {
                    AccessoryBagOverlay.resetCache();
                }
            } else {
                AccessoryBagOverlay.resetCache();
            }

            if(neu.hasSkyblockScoreboard()) {
                if(Loader.isModLoaded("morus")) {
                    MorusIntegration.getInstance().tick();
                }
                lastSkyblockScoreboard = currentTime;
                if(!joinedSB) {
                    joinedSB = true;

                    SBGamemodes.loadFromFile();

                    if(neu.config.notifications.showUpdateMsg) {
                        displayUpdateMessageIfOutOfDate();
                    }

                    if(neu.config.hidden.doRamNotif) {
                        long maxMemoryMB = Runtime.getRuntime().maxMemory()/1024L/1024L;
                        if(maxMemoryMB > 4100) {
                            notificationDisplayMillis = System.currentTimeMillis();
                            notificationLines = new ArrayList<>();
                            notificationLines.add(EnumChatFormatting.DARK_RED+"Too much memory allocated!");
                            notificationLines.add(String.format(EnumChatFormatting.DARK_GRAY+"NEU has detected %03dMB of memory allocated to Minecraft!", maxMemoryMB));
                            notificationLines.add(EnumChatFormatting.DARK_GRAY+"It is recommended to allocated between 2-4GB of memory");
                            notificationLines.add(EnumChatFormatting.DARK_GRAY+"More than 4GB WILL cause FPS issues, EVEN if you have 16GB+ available");
                            notificationLines.add("");
                            notificationLines.add(EnumChatFormatting.DARK_GRAY+"For more information, visit #ram-info in discord.gg/spr6ESn");
                        }
                    }

                    if(!neu.config.hidden.loadedModBefore) {
                        neu.config.hidden.loadedModBefore = true;

                        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(""));
                        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(
                                EnumChatFormatting.BLUE+"It seems this is your first time using NotEnoughUpdates."));
                        ChatComponentText clickText = new ChatComponentText(
                                EnumChatFormatting.YELLOW+"Click this message if you would like to view a short tutorial.");
                        clickText.setChatStyle(Utils.createClickStyle(ClickEvent.Action.RUN_COMMAND, "/neututorial"));
                        Minecraft.getMinecraft().thePlayer.addChatMessage(clickText);
                        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(""));
                    }
                }
                SBInfo.getInstance().tick();
            }
            if(currentTime - lastSkyblockScoreboard < 5*60*1000) { //5 minutes
                neu.manager.auctionManager.tick();
            } else {
                neu.manager.auctionManager.markNeedsUpdate();
            }
        }
        if(longUpdate && neu.hasSkyblockScoreboard()) {
            /*if(neu.manager.getCurrentProfile() == null || neu.manager.getCurrentProfile().length() == 0) {
                ProfileViewer.Profile profile = NotEnoughUpdates.profileViewer.getProfile(Minecraft.getMinecraft().thePlayer.getUniqueID().toString().replace("-", ""),
                        callback->{});
                if(profile != null) {
                    String latest = profile.getLatestProfile();
                    if(latest != null) {
                        neu.manager.setCurrentProfileBackup(profile.getLatestProfile());
                    }
                }
            }*/
            /*if(neu.manager.getCurrentProfile() != null && neu.manager.getCurrentProfile().length() > 0) {
                HashSet<String> newItem = new HashSet<>();
                if(Minecraft.getMinecraft().currentScreen instanceof GuiContainer &&
                        !(Minecraft.getMinecraft().currentScreen instanceof GuiCrafting)) {
                    boolean usableContainer = true;
                    for(ItemStack stack : Minecraft.getMinecraft().thePlayer.openContainer.getInventory()) {
                        if(stack == null) {
                            continue;
                        }
                        if(stack.hasTagCompound()) {
                            NBTTagCompound tag = stack.getTagCompound();
                            if(tag.hasKey("ExtraAttributes", 10)) {
                                continue;
                            }
                        }
                        usableContainer = false;
                        break;
                    }
                    if(!usableContainer) {
                        if(Minecraft.getMinecraft().currentScreen instanceof GuiChest) {
                            GuiChest chest = (GuiChest) Minecraft.getMinecraft().currentScreen;
                            ContainerChest container = (ContainerChest) chest.inventorySlots;
                            String containerName = container.getLowerChestInventory().getDisplayName().getUnformattedText();

                            if(containerName.equals("Accessory Bag") || containerName.startsWith("Wardrobe")) {
                                usableContainer = true;
                            }
                        }
                    }
                    if(usableContainer) {
                        for(ItemStack stack : Minecraft.getMinecraft().thePlayer.inventory.mainInventory) {
                            processUniqueStack(stack, newItem);
                        }
                        for(ItemStack stack : Minecraft.getMinecraft().thePlayer.openContainer.getInventory()) {
                            processUniqueStack(stack, newItem);
                        }
                    }
                } else {
                    for(ItemStack stack : Minecraft.getMinecraft().thePlayer.inventory.mainInventory) {
                        processUniqueStack(stack, newItem);
                    }
                }
                newItemAddMap.keySet().retainAll(newItem);
            }*/
        }
    }

    private void processUniqueStack(ItemStack stack, HashSet<String> newItem) {
        if(stack != null && stack.hasTagCompound()) {
            String internalname = neu.manager.getInternalNameForItem(stack);
            if(internalname != null) {
                /*ArrayList<String> log = neu.manager.config.collectionLog.value.computeIfAbsent(
                        neu.manager.getCurrentProfile(), k -> new ArrayList<>());
                if(!log.contains(internalname)) {
                    newItem.add(internalname);
                    if(newItemAddMap.containsKey(internalname)) {
                        if(System.currentTimeMillis() - newItemAddMap.get(internalname) > 1000) {
                            log.add(internalname);
                            try { neu.manager.saveConfig(); } catch(IOException ignored) {}
                        }
                    } else {
                        newItemAddMap.put(internalname, System.currentTimeMillis());
                    }
                }*/
            }
        }
    }

    @SubscribeEvent(priority= EventPriority.HIGHEST)
    public void onRenderEntitySpecials(RenderLivingEvent.Specials.Pre event) {
        if(Minecraft.getMinecraft().currentScreen instanceof GuiProfileViewer) {
            if(((GuiProfileViewer)Minecraft.getMinecraft().currentScreen).getEntityPlayer() == event.entity) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onRenderGameOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (event.type != null && event.type.equals(RenderGameOverlayEvent.ElementType.BOSSHEALTH) &&
                Minecraft.getMinecraft().currentScreen instanceof GuiContainer && neu.overlay.isUsingMobsFilter()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderGameOverlayPost(RenderGameOverlayEvent.Post event) {
        long timeRemaining = 15000 - (System.currentTimeMillis() - notificationDisplayMillis);
        if(event.type == RenderGameOverlayEvent.ElementType.ALL) {
            DungeonWin.render(event.partialTicks);
        }
        if(event.type == RenderGameOverlayEvent.ElementType.ALL &&
                timeRemaining > 0 && notificationLines != null && notificationLines.size() > 0) {
            int width = 0;
            int height = notificationLines.size()*10+10;
            
            for(String line : notificationLines) {
                int len = Minecraft.getMinecraft().fontRendererObj.getStringWidth(line) + 8;
                if(len > width) {
                    width = len;
                }
            }

            ScaledResolution sr = Utils.pushGuiScale(2);

            int midX = sr.getScaledWidth()/2;
            int topY = sr.getScaledHeight()*3/4-height/2;
            Gui.drawRect(midX-width/2, sr.getScaledHeight()*3/4-height/2,
                    midX+width/2, sr.getScaledHeight()*3/4+height/2, 0xFF3C3C3C);
            Gui.drawRect(midX-width/2+2, sr.getScaledHeight()*3/4-height/2+2,
                    midX+width/2-2, sr.getScaledHeight()*3/4+height/2-2, 0xFFC8C8C8);

            Minecraft.getMinecraft().fontRendererObj.drawString((timeRemaining/1000)+"s", midX-width/2+3,
                    topY+3, 0xFF000000, false);

            Utils.drawStringCentered(notificationLines.get(0), Minecraft.getMinecraft().fontRendererObj,
                    midX, topY+4+5, false, -1);
            for(int i=1; i<notificationLines.size(); i++) {
                String line = notificationLines.get(i);
                Utils.drawStringCentered(line, Minecraft.getMinecraft().fontRendererObj,
                        midX, topY+4+5+2+i*10, false, -1);
            }

            Utils.pushGuiScale(-1);
        }
    }

    /**
     * When opening a GuiContainer, will reset the overlay and load the config.
     * When closing a GuiContainer, will save the config.
     * Also includes a dev feature used for automatically acquiring crafting information from the "Crafting Table" GUI.
     */
    AtomicBoolean missingRecipe = new AtomicBoolean(false);
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if(!(event.gui instanceof GuiContainer) && Minecraft.getMinecraft().currentScreen != null) {
            CalendarOverlay.setEnabled(false);
        }

        neu.manager.auctionManager.customAH.lastGuiScreenSwitch = System.currentTimeMillis();
        BetterContainers.reset();

        if(event.gui == null && neu.manager.auctionManager.customAH.isRenderOverAuctionView() &&
                !(Minecraft.getMinecraft().currentScreen instanceof CustomAHGui)) {
            event.gui = new CustomAHGui();
        }

        if(!(event.gui instanceof GuiChest || event.gui instanceof GuiEditSign)) {
            neu.manager.auctionManager.customAH.setRenderOverAuctionView(false);
        } else if(event.gui instanceof GuiChest && (neu.manager.auctionManager.customAH.isRenderOverAuctionView() ||
                Minecraft.getMinecraft().currentScreen instanceof CustomAHGui)){
            GuiChest chest = (GuiChest) event.gui;
            ContainerChest container = (ContainerChest) chest.inventorySlots;
            String containerName = container.getLowerChestInventory().getDisplayName().getUnformattedText();

            neu.manager.auctionManager.customAH.setRenderOverAuctionView(containerName.trim().equals("Auction View") ||
                    containerName.trim().equals("BIN Auction View") || containerName.trim().equals("Confirm Bid") ||
                    containerName.trim().equals("Confirm Purchase"));
        }

        //OPEN
        if(Minecraft.getMinecraft().currentScreen == null
                && event.gui instanceof GuiContainer) {
            neu.overlay.reset();
        }
        if(event.gui != null && neu.config.hidden.dev) {
            if(event.gui instanceof GuiChest) {
                GuiChest eventGui = (GuiChest) event.gui;
                ContainerChest cc = (ContainerChest) eventGui.inventorySlots;
                IInventory lower = cc.getLowerChestInventory();
                ses.schedule(() -> {
                    if(Minecraft.getMinecraft().currentScreen != event.gui) {
                        return;
                    }
                    if(lower.getStackInSlot(23).getDisplayName().endsWith("Crafting Table")) {
                        try {
                            ItemStack res = lower.getStackInSlot(25);
                            String resInternalname = neu.manager.getInternalNameForItem(res);

                            if(lower.getStackInSlot(48) != null) {
                                String backName = null;
                                NBTTagCompound tag = lower.getStackInSlot(48).getTagCompound();
                                if(tag.hasKey("display", 10)) {
                                    NBTTagCompound nbttagcompound = tag.getCompoundTag("display");
                                    if(nbttagcompound.getTagId("Lore") == 9){
                                        NBTTagList nbttaglist1 = nbttagcompound.getTagList("Lore", 8);
                                        backName = nbttaglist1.getStringTagAt(0);
                                    }
                                }

                                if(backName != null) {
                                    String[] split = backName.split(" ");
                                    if(split[split.length-1].contains("Rewards")) {
                                        String col = backName.substring(split[0].length()+1,
                                                backName.length()-split[split.length-1].length()-1);

                                        JsonObject json = neu.manager.getItemInformation().get(resInternalname);
                                        json.addProperty("crafttext", "Requires: " + col);

                                        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("Added: " + resInternalname));
                                        neu.manager.writeJsonDefaultDir(json, resInternalname+".json");
                                        neu.manager.loadItem(resInternalname);
                                    }
                                }
                            }

                            /*JsonArray arr = null;
                            File f = new File(neu.manager.configLocation, "missing.json");
                            try(InputStream instream = new FileInputStream(f)) {
                                BufferedReader reader = new BufferedReader(new InputStreamReader(instream, StandardCharsets.UTF_8));
                                JsonObject json = neu.manager.gson.fromJson(reader, JsonObject.class);
                                arr = json.getAsJsonArray("missing");
                            } catch(IOException e) {}
                            try {
                                JsonObject json = new JsonObject();
                                JsonArray newArr = new JsonArray();
                                for(JsonElement e : arr) {
                                    if(!e.getAsString().equals(resInternalname)) {
                                        newArr.add(e);
                                    }
                                }
                                json.add("missing", newArr);
                                neu.manager.writeJson(json, f);
                            } catch(IOException e) {}*/



                            /*JsonObject recipe = new JsonObject();

                            String[] x = {"1","2","3"};
                            String[] y = {"A","B","C"};

                            for(int i=0; i<=18; i+=9) {
                                for(int j=0; j<3; j++) {
                                    ItemStack stack = lower.getStackInSlot(10+i+j);
                                    String internalname = "";
                                    if(stack != null) {
                                        internalname = neu.manager.getInternalNameForItem(stack);
                                        if(!neu.manager.getItemInformation().containsKey(internalname)) {
                                            neu.manager.writeItemToFile(stack);
                                        }
                                        internalname += ":"+stack.stackSize;
                                    }
                                    recipe.addProperty(y[i/9]+x[j], internalname);
                                }
                            }

                            JsonObject json = neu.manager.getJsonForItem(res);
                            json.add("recipe", recipe);
                            json.addProperty("internalname", resInternalname);
                            json.addProperty("clickcommand", "viewrecipe");
                            json.addProperty("modver", NotEnoughUpdates.VERSION);

                            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("Added: " + resInternalname));
                            neu.manager.writeJsonDefaultDir(json, resInternalname+".json");
                            neu.manager.loadItem(resInternalname);*/
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, 200, TimeUnit.MILLISECONDS);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(EntityInteractEvent event) {
        if(!event.isCanceled() && NotEnoughUpdates.INSTANCE.hasSkyblockScoreboard() &&
                Minecraft.getMinecraft().thePlayer.isSneaking() &&
                Minecraft.getMinecraft().ingameGUI != null) {
            if(event.target instanceof EntityPlayer) {
                for(NetworkPlayerInfo info : Minecraft.getMinecraft().thePlayer.sendQueue.getPlayerInfoMap()) {
                    String name = Minecraft.getMinecraft().ingameGUI.getTabList().getPlayerName(info);
                    if(name.contains("Status: "+EnumChatFormatting.RESET+EnumChatFormatting.BLUE+"Guest")) {
                        NotEnoughUpdates.INSTANCE.sendChatMessage("/trade " + event.target.getName());
                        event.setCanceled(true);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 1) When receiving "You are playing on profile" messages, will set the current profile.
     * 2) When a /viewrecipe command fails (i.e. player does not have recipe unlocked, will open the custom recipe GUI)
     * 3) Replaces lobby join notifications when streamer mode is active
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onGuiChat(ClientChatReceivedEvent e) {
        DungeonWin.onChatMessage(e);

        String r = null;
        String unformatted = Utils.cleanColour(e.message.getUnformattedText());
        if(unformatted.startsWith("You are playing on profile: ")) {
            neu.manager.setCurrentProfile(unformatted.substring("You are playing on profile: ".length()).split(" ")[0].trim());
        } else if(unformatted.startsWith("Your profile was changed to: ")) {//Your profile was changed to:
            neu.manager.setCurrentProfile(unformatted.substring("Your profile was changed to: ".length()).split(" ")[0].trim());
        } else if(unformatted.startsWith("Your new API key is ")) {
            neu.config.apiKey.apiKey = unformatted.substring("Your new API key is ".length());
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW+
                    "[NEU] API Key automatically configured"));
        }
        if(e.message.getFormattedText().equals(EnumChatFormatting.RESET.toString()+
                EnumChatFormatting.RED+"You haven't unlocked this recipe!"+EnumChatFormatting.RESET)) {
            r =  EnumChatFormatting.RED+"You haven't unlocked this recipe!";
        } else if(e.message.getFormattedText().startsWith(EnumChatFormatting.RESET.toString()+
                EnumChatFormatting.RED+"Invalid recipe ")) {
            r = "";
        }
        if(e.message.getFormattedText().contains(EnumChatFormatting.YELLOW+"Visit the Auction House to collect your item!")) {
            if(NotEnoughUpdates.INSTANCE.manager.auctionManager.customAH.latestBid != null &&
                    System.currentTimeMillis() - NotEnoughUpdates.INSTANCE.manager.auctionManager.customAH.latestBidMillis < 5000) {
                NotEnoughUpdates.INSTANCE.sendChatMessage("/viewauction " +
                        NotEnoughUpdates.INSTANCE.manager.auctionManager.customAH.niceAucId(
                                NotEnoughUpdates.INSTANCE.manager.auctionManager.customAH.latestBid));
            }
        }
        if(r != null) {
            if(neu.manager.failViewItem(r)) {
                e.setCanceled(true);
            }
            missingRecipe.set(true);
        }
        //System.out.println(e.message);
        if(unformatted.startsWith("Sending to server") &&
                neu.isOnSkyblock() && neu.config.misc.streamerMode && e.message instanceof ChatComponentText) {
            String m = e.message.getFormattedText();
            String m2 = StreamerMode.filterChat(e.message.getFormattedText());
            if(!m.equals(m2)) {
                e.message = new ChatComponentText(m2);
            }
        }
    }

    /**
     * Sets hoverInv and focusInv variables, representing whether the NEUOverlay should render behind the inventory when
     * (hoverInv == true) and whether mouse/kbd inputs shouldn't be sent to NEUOverlay (focusInv == true).
     *
     * If hoverInv is true, will render the overlay immediately (resulting in the inventory being drawn over the GUI)
     * If hoverInv is false, the overlay will render in #onGuiScreenDraw (resulting in the GUI being drawn over the inv)
     *
     * All of this only matters if players are using gui scale auto which may result in the inventory being drawn
     * over the various panes.
     * @param event
     */
    @SubscribeEvent
    public void onGuiBackgroundDraw(GuiScreenEvent.BackgroundDrawnEvent event) {
        if((shouldRenderOverlay(event.gui) || event.gui instanceof CustomAHGui) && neu.isOnSkyblock()) {
            ScaledResolution scaledresolution = new ScaledResolution(Minecraft.getMinecraft());
            int width = scaledresolution.getScaledWidth();

            boolean hoverPane = event.getMouseX() < width*neu.overlay.getInfoPaneOffsetFactor() ||
                    event.getMouseX() > width*neu.overlay.getItemPaneOffsetFactor();

            if(event.gui instanceof GuiContainer) {
                try {
                    int xSize = (int) Utils.getField(GuiContainer.class, event.gui, "xSize", "field_146999_f");
                    int ySize = (int) Utils.getField(GuiContainer.class, event.gui, "ySize", "field_147000_g");
                    int guiLeft = (int) Utils.getField(GuiContainer.class, event.gui, "guiLeft", "field_147003_i");
                    int guiTop = (int) Utils.getField(GuiContainer.class, event.gui, "guiTop", "field_147009_r");

                    hoverInv = event.getMouseX() > guiLeft && event.getMouseX() < guiLeft + xSize &&
                            event.getMouseY() > guiTop && event.getMouseY() < guiTop + ySize;

                    if(hoverPane) {
                        if(!hoverInv) focusInv = false;
                    } else {
                        focusInv = true;
                    }
                } catch(NullPointerException npe) {
                    npe.printStackTrace();
                    focusInv = !hoverPane;
                }
            }
            if(event.gui instanceof GuiItemRecipe) {
                GuiItemRecipe guiItemRecipe = ((GuiItemRecipe)event.gui);
                hoverInv = event.getMouseX() > guiItemRecipe.guiLeft && event.getMouseX() < guiItemRecipe.guiLeft + guiItemRecipe.xSize &&
                        event.getMouseY() > guiItemRecipe.guiTop && event.getMouseY() < guiItemRecipe.guiTop + guiItemRecipe.ySize;

                if(hoverPane) {
                    if(!hoverInv) focusInv = false;
                } else {
                    focusInv = true;
                }
            }
            if(focusInv) {
                try {
                    neu.overlay.render(hoverInv && focusInv);
                } catch(ConcurrentModificationException e) {e.printStackTrace();}
                GL11.glTranslatef(0, 0, 10);
            }
        }
    }

    @SubscribeEvent
    public void onGuiScreenDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if(TradeWindow.tradeWindowActive() ||
                event.gui instanceof CustomAHGui || neu.manager.auctionManager.customAH.isRenderOverAuctionView()) {
            event.setCanceled(true);

            ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());
            int width = scaledResolution.getScaledWidth();
            int height = scaledResolution.getScaledHeight();

            //Dark background
            Utils.drawGradientRect(0, 0, width, height, -1072689136, -804253680);

            if(event.mouseX < width*neu.overlay.getWidthMult()/3 || event.mouseX > width-width*neu.overlay.getWidthMult()/3) {
                if(event.gui instanceof CustomAHGui || neu.manager.auctionManager.customAH.isRenderOverAuctionView()) {
                    neu.manager.auctionManager.customAH.drawScreen(event.mouseX, event.mouseY);
                } else {
                    TradeWindow.render(event.mouseX, event.mouseY);
                }
                neu.overlay.render(false);
            } else {
                neu.overlay.render(false);
                if(event.gui instanceof CustomAHGui || neu.manager.auctionManager.customAH.isRenderOverAuctionView()) {
                    neu.manager.auctionManager.customAH.drawScreen(event.mouseX, event.mouseY);
                } else {
                    TradeWindow.render(event.mouseX, event.mouseY);
                }
            }
        }
    }

    private static boolean shouldRenderOverlay(Gui gui) {
        boolean validGui = gui instanceof GuiContainer || gui instanceof GuiItemRecipe;
        if(gui instanceof GuiChest) {
            GuiChest eventGui = (GuiChest) gui;
            ContainerChest cc = (ContainerChest) eventGui.inventorySlots;
            String containerName = cc.getLowerChestInventory().getDisplayName().getUnformattedText();
            if(containerName.trim().equals("Fast Travel")) {
                validGui = false;
            }
        }
        return validGui;
    }

    /**
     * Will draw the NEUOverlay over the inventory if focusInv == false. (z-translation of 300 is so that NEUOverlay
     * will draw over Items in the inventory (which render at a z value of about 250))
     * @param event
     */
    @SubscribeEvent
    public void onGuiScreenDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if(!(TradeWindow.tradeWindowActive() || event.gui instanceof CustomAHGui ||
                neu.manager.auctionManager.customAH.isRenderOverAuctionView())) {
            if(shouldRenderOverlay(event.gui) && neu.isOnSkyblock()) {
                GlStateManager.pushMatrix();
                if(!focusInv) {
                    GL11.glTranslatef(0, 0, 300);
                    neu.overlay.render(hoverInv && focusInv);
                    GL11.glTranslatef(0, 0, -300);
                }
                neu.overlay.renderOverlay();
                GlStateManager.popMatrix();
            }
        }

        if(shouldRenderOverlay(event.gui) && neu.isOnSkyblock()) {
            renderDungeonChestOverlay(event.gui);
            if(neu.config.accessoryBag.enableOverlay) {
                AccessoryBagOverlay.renderOverlay();
            }
        }
    }

    private void renderDungeonChestOverlay(GuiScreen gui) {
        if(gui instanceof GuiChest && neu.config.dungeonProfit.profitDisplayLoc != 2) {
            try {
                int xSize = (int) Utils.getField(GuiContainer.class, gui, "xSize", "field_146999_f");
                int ySize = (int) Utils.getField(GuiContainer.class, gui, "ySize", "field_147000_g");
                int guiLeft = (int) Utils.getField(GuiContainer.class, gui, "guiLeft", "field_147003_i");
                int guiTop = (int) Utils.getField(GuiContainer.class, gui, "guiTop", "field_147009_r");

                GuiChest eventGui = (GuiChest) gui;
                ContainerChest cc = (ContainerChest) eventGui.inventorySlots;
                IInventory lower = cc.getLowerChestInventory();

                ItemStack rewardChest = lower.getStackInSlot(31);
                if (rewardChest != null && rewardChest.getDisplayName().endsWith(EnumChatFormatting.GREEN+"Open Reward Chest")) {
                    int chestCost = 0;
                    try {
                        String line6 = Utils.cleanColour(neu.manager.getLoreFromNBT(rewardChest.getTagCompound())[6]);
                        StringBuilder cost = new StringBuilder();
                        for(int i=0; i<line6.length(); i++) {
                            char c = line6.charAt(i);
                            if("0123456789".indexOf(c) >= 0) {
                                cost.append(c);
                            }
                        }
                        if(cost.length() > 0) {
                            chestCost = Integer.parseInt(cost.toString());
                        }
                    } catch(Exception ignored) {}

                    String missingItem = null;
                    int totalValue = 0;
                    HashMap<String, Float> itemValues = new HashMap<>();
                    for(int i=0; i<5; i++) {
                        ItemStack item = lower.getStackInSlot(11+i);
                        String internal = neu.manager.getInternalNameForItem(item);
                        if(internal != null) {
                            internal = internal.replace("\u00CD", "I").replace("\u0130", "I");
                            float bazaarPrice = -1;
                            JsonObject bazaarInfo = neu.manager.auctionManager.getBazaarInfo(internal);
                            if(bazaarInfo != null && bazaarInfo.has("avg_sell")) {
                                bazaarPrice = bazaarInfo.get("avg_sell").getAsFloat();
                            }

                            float worth = -1;
                            if(bazaarPrice > 0) {
                                worth = bazaarPrice;
                            } else {
                                switch(neu.config.dungeonProfit.profitType) {
                                    case 1:
                                        worth = neu.manager.auctionManager.getItemAvgBin(internal);
                                        break;
                                    case 2:
                                        JsonObject auctionInfo = neu.manager.auctionManager.getItemAuctionInfo(internal);
                                        if(auctionInfo != null) {
                                            if(auctionInfo.has("clean_price")) {
                                                worth = (int)auctionInfo.get("clean_price").getAsFloat();
                                            } else {
                                                worth = (int)(auctionInfo.get("price").getAsFloat() / auctionInfo.get("count").getAsFloat());
                                            }
                                        }
                                        break;
                                    default:
                                        worth = neu.manager.auctionManager.getLowestBin(internal);
                                }
                                if(worth <= 0) {
                                    worth = neu.manager.auctionManager.getLowestBin(internal);
                                    if(worth <= 0) {
                                        worth = neu.manager.auctionManager.getItemAvgBin(internal);
                                        if(worth <= 0) {
                                            JsonObject auctionInfo = neu.manager.auctionManager.getItemAuctionInfo(internal);
                                            if(auctionInfo != null) {
                                                if(auctionInfo.has("clean_price")) {
                                                    worth = (int)auctionInfo.get("clean_price").getAsFloat();
                                                } else {
                                                    worth = (int)(auctionInfo.get("price").getAsFloat() / auctionInfo.get("count").getAsFloat());
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if(worth > 0 && totalValue >= 0) {
                                totalValue += worth;
                                String display = item.getDisplayName();

                                if(display.contains("Enchanted Book")) {
                                    NBTTagCompound tag = item.getTagCompound();
                                    if(tag != null && tag.hasKey("ExtraAttributes", 10)) {
                                        NBTTagCompound ea = tag.getCompoundTag("ExtraAttributes");
                                        NBTTagCompound enchants = ea.getCompoundTag("enchantments");

                                        int highestLevel = -1;
                                        for(String enchname : enchants.getKeySet()) {
                                            int level = enchants.getInteger(enchname);
                                            if(level > highestLevel) {
                                                display = EnumChatFormatting.BLUE+WordUtils.capitalizeFully(
                                                        enchname.replace("_", " ")
                                                                .replace("Ultimate", "")
                                                                .trim()) + " " + level;
                                            }
                                        }
                                    }
                                }

                                itemValues.put(display, worth);
                            } else {
                                if(totalValue != -1) {
                                    missingItem = internal;
                                }
                                totalValue = -1;
                            }
                        }
                    }

                    NumberFormat format = NumberFormat.getInstance(Locale.US);
                    String valueStringBIN1;
                    String valueStringBIN2;
                    if(totalValue >= 0) {
                        valueStringBIN1 = EnumChatFormatting.YELLOW+"Value (BIN): ";
                        valueStringBIN2 = EnumChatFormatting.GOLD + format.format(totalValue) + " coins";
                    } else {
                        valueStringBIN1 = EnumChatFormatting.YELLOW+"Can't find BIN: ";
                        valueStringBIN2 = missingItem;
                    }

                    int profitLossBIN = totalValue - chestCost;

                    String profitPrefix =  EnumChatFormatting.DARK_GREEN.toString();
                    String lossPrefix = EnumChatFormatting.RED.toString();
                    String prefix = profitLossBIN >= 0 ? profitPrefix : lossPrefix;

                    String plStringBIN;
                    if(profitLossBIN >= 0) {
                        plStringBIN = prefix + "+" + format.format(profitLossBIN) + " coins";
                    } else {
                        plStringBIN = prefix + "-" + format.format(-profitLossBIN) + " coins";
                    }

                    if(neu.config.dungeonProfit.profitDisplayLoc == 1 && !valueStringBIN2.equals(missingItem)) {
                        int w = Minecraft.getMinecraft().fontRendererObj.getStringWidth(plStringBIN);
                        GlStateManager.disableLighting();
                        Minecraft.getMinecraft().fontRendererObj.drawString(plStringBIN, guiLeft+xSize-5-w, guiTop+5,
                                0xffffffff, true);
                        return;
                    }

                    Minecraft.getMinecraft().getTextureManager().bindTexture(dungeon_chest_worth);
                    GL11.glColor4f(1, 1, 1, 1);
                    GlStateManager.disableLighting();
                    Utils.drawTexturedRect(guiLeft+xSize+4, guiTop, 180, 101, 0, 180/256f, 0, 101/256f, GL11.GL_NEAREST);

                    Utils.renderAlignedString(valueStringBIN1, valueStringBIN2,
                            guiLeft+xSize+4+10, guiTop+14, 160);
                    if(totalValue >= 0) {
                        Utils.renderAlignedString(EnumChatFormatting.YELLOW+"Profit/Loss: ", plStringBIN,
                                guiLeft+xSize+4+10, guiTop+24, 160);
                    }

                    int index=0;
                    for(Map.Entry<String, Float> entry : itemValues.entrySet()) {
                        Utils.renderAlignedString(entry.getKey(), prefix+
                                        format.format(entry.getValue().intValue()),
                                guiLeft+xSize+4+10, guiTop+29+(++index)*10, 160);
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void drawStringShadow(String str, float x, float y, int len) {
        for(int xOff=-2; xOff<=2; xOff++) {
            for(int yOff=-2; yOff<=2; yOff++) {
                if(Math.abs(xOff) != Math.abs(yOff)) {
                    Utils.drawStringCenteredScaledMaxWidth(Utils.cleanColourNotModifiers(str),
                            Minecraft.getMinecraft().fontRendererObj,
                            x+xOff/2f, y+yOff/2f, false, len,
                            new Color(20, 20, 20, 100/Math.max(Math.abs(xOff), Math.abs(yOff))).getRGB());
                }
            }
        }

        Utils.drawStringCenteredScaledMaxWidth(str,
                Minecraft.getMinecraft().fontRendererObj,
                x, y, false, len,
                new Color(64, 64, 64, 255).getRGB());
    }

    /**
     * Sends a mouse event to NEUOverlay if the inventory isn't hovered AND focused.
     * Will also cancel the event if if NEUOverlay#mouseInput returns true.
     * @param event
     */
    @SubscribeEvent
    public void onGuiScreenMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if(!event.isCanceled()) {
            Utils.scrollTooltip(Mouse.getEventDWheel());
        }
        if(TradeWindow.tradeWindowActive() || event.gui instanceof CustomAHGui ||
                neu.manager.auctionManager.customAH.isRenderOverAuctionView()) {
            event.setCanceled(true);
            if(event.gui instanceof CustomAHGui ||
                    neu.manager.auctionManager.customAH.isRenderOverAuctionView()) {
                neu.manager.auctionManager.customAH.handleMouseInput();
            } else {
                TradeWindow.handleMouseInput();
            }
            neu.overlay.mouseInput();
            return;
        }
        if(shouldRenderOverlay(event.gui) && neu.isOnSkyblock()) {
            if(!neu.config.accessoryBag.enableOverlay || !AccessoryBagOverlay.mouseClick()) {
                if(!(hoverInv && focusInv)) {
                    if(neu.overlay.mouseInput()) {
                        event.setCanceled(true);
                    }
                } else {
                    neu.overlay.mouseInputInv();
                }
            }
        }
    }

    ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);

    /**
     * Sends a kbd event to NEUOverlay, cancelling if NEUOverlay#keyboardInput returns true.
     * Also includes a dev function used for creating custom named json files with recipes.
     */
    @SubscribeEvent
    public void onGuiScreenKeyboard(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if(TradeWindow.tradeWindowActive() || event.gui instanceof CustomAHGui ||
                neu.manager.auctionManager.customAH.isRenderOverAuctionView()) {
            if(event.gui instanceof CustomAHGui ||
                    neu.manager.auctionManager.customAH.isRenderOverAuctionView()) {
                if(neu.manager.auctionManager.customAH.keyboardInput()) {
                    event.setCanceled(true);
                    Minecraft.getMinecraft().dispatchKeypresses();
                } else if(neu.overlay.keyboardInput(focusInv)) {
                    event.setCanceled(true);
                }
            } else {
                TradeWindow.keyboardInput();
                if(Keyboard.getEventKey() != Keyboard.KEY_ESCAPE) {
                    event.setCanceled(true);
                    Minecraft.getMinecraft().dispatchKeypresses();
                    neu.overlay.keyboardInput(focusInv);
                }
            }
            return;
        }

        if(shouldRenderOverlay(event.gui) && neu.isOnSkyblock()) {
            if(neu.overlay.keyboardInput(focusInv)) {
                event.setCanceled(true);
            }
        }
        if(neu.config.hidden.dev && neu.config.hidden.enableItemEditing && Minecraft.getMinecraft().theWorld != null &&
                Keyboard.getEventKey() == Keyboard.KEY_O && Keyboard.getEventKeyState()) {
            GuiScreen gui = Minecraft.getMinecraft().currentScreen;
            if(gui instanceof GuiChest) {
                GuiChest eventGui = (GuiChest) event.gui;
                ContainerChest cc = (ContainerChest) eventGui.inventorySlots;
                IInventory lower = cc.getLowerChestInventory();

                if(lower.getStackInSlot(23) != null &&
                        lower.getStackInSlot(23).getDisplayName().endsWith("Crafting Table")) {
                    ItemStack res = lower.getStackInSlot(25);
                    String resInternalname = neu.manager.getInternalNameForItem(res);
                    JTextField tf = new JTextField();
                    tf.setText(resInternalname);
                    tf.addAncestorListener(new RequestFocusListener());
                    JOptionPane.showOptionDialog(null,
                            tf,
                            "Enter Name:",
                            JOptionPane.NO_OPTION,
                            JOptionPane.PLAIN_MESSAGE,
                            null, new String[]{"Enter"}, "Enter");
                    resInternalname = tf.getText();
                    if(resInternalname.trim().length() == 0) {
                        return;
                    }

                    JsonObject recipe = new JsonObject();

                    String[] x = {"1","2","3"};
                    String[] y = {"A","B","C"};

                    for(int i=0; i<=18; i+=9) {
                        for(int j=0; j<3; j++) {
                            ItemStack stack = lower.getStackInSlot(10+i+j);
                            String internalname = "";
                            if(stack != null) {
                                internalname = neu.manager.getInternalNameForItem(stack);
                                if(!neu.manager.getItemInformation().containsKey(internalname)) {
                                    neu.manager.writeItemToFile(stack);
                                }
                                internalname += ":"+stack.stackSize;
                            }
                            recipe.addProperty(y[i/9]+x[j], internalname);
                        }
                    }

                    JsonObject json = neu.manager.getJsonForItem(res);
                    json.add("recipe", recipe);
                    json.addProperty("internalname", resInternalname);
                    json.addProperty("clickcommand", "viewrecipe");
                    json.addProperty("modver", NotEnoughUpdates.VERSION);
                    try {
                        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("Added: " + resInternalname));
                        neu.manager.writeJsonDefaultDir(json, resInternalname+".json");
                        neu.manager.loadItem(resInternalname);
                    } catch(IOException e) {}
                }
            }
        }
    }

    private static String[] rarityArrC = new String[] {
            EnumChatFormatting.WHITE+EnumChatFormatting.BOLD.toString()+"COMMON",
            EnumChatFormatting.GREEN+EnumChatFormatting.BOLD.toString()+"UNCOMMON",
            EnumChatFormatting.BLUE+EnumChatFormatting.BOLD.toString()+"RARE",
            EnumChatFormatting.DARK_PURPLE+EnumChatFormatting.BOLD.toString()+"EPIC",
            EnumChatFormatting.GOLD+EnumChatFormatting.BOLD.toString()+"LEGENDARY",
            EnumChatFormatting.LIGHT_PURPLE+EnumChatFormatting.BOLD.toString()+"MYTHIC",
            EnumChatFormatting.RED+EnumChatFormatting.BOLD.toString()+"SPECIAL",
            EnumChatFormatting.RED+EnumChatFormatting.BOLD.toString()+"VERY SPECIAL",
            EnumChatFormatting.DARK_RED+EnumChatFormatting.BOLD.toString()+"SUPREME",
    };
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onItemTooltipLow(ItemTooltipEvent event) {
        if(!NotEnoughUpdates.INSTANCE.isOnSkyblock()) return;

        String internalname = NotEnoughUpdates.INSTANCE.manager.getInternalNameForItem(event.itemStack);
        if(internalname == null) {
            return;
        }

        boolean hasEnchantments = event.itemStack.getTagCompound().getCompoundTag("ExtraAttributes").hasKey("enchantments", 10);
        Set<String> enchantIds = new HashSet<>();
        if(hasEnchantments) enchantIds = event.itemStack.getTagCompound().getCompoundTag("ExtraAttributes").getCompoundTag("enchantments").getKeySet();

        JsonObject enchantsConst = Constants.ENCHANTS;
        JsonArray allItemEnchs = null;
        Set<String> ignoreFromPool = new HashSet<>();
        if(enchantsConst != null && hasEnchantments && neu.config.tooltipTweaks.missingEnchantList) {
            try {
                JsonArray enchantPools = enchantsConst.get("enchant_pools").getAsJsonArray();
                for(JsonElement element : enchantPools) {
                    Set<String> currentPool = new HashSet<>();
                    for(JsonElement poolElement : element.getAsJsonArray()) {
                        String poolS = poolElement.getAsString();
                        currentPool.add(poolS);
                    }
                    for(JsonElement poolElement : element.getAsJsonArray()) {
                        String poolS = poolElement.getAsString();
                        if(enchantIds.contains(poolS)) {
                            ignoreFromPool.addAll(currentPool);
                            break;
                        }
                    }
                }

                JsonObject enchantsObj = enchantsConst.get("enchants").getAsJsonObject();
                NBTTagCompound tag = event.itemStack.getTagCompound();
                if(tag != null) {
                    NBTTagCompound display = tag.getCompoundTag("display");
                    if (display.hasKey("Lore", 9)) {
                        NBTTagList list = display.getTagList("Lore", 8);
                        out:
                        for (int i = list.tagCount(); i >= 0; i--) {
                            String line = list.getStringTagAt(i);
                            for(int j=0; j<rarityArrC.length; j++) {
                                for(Map.Entry<String, JsonElement> entry : enchantsObj.entrySet()) {
                                    if(line.contains(rarityArrC[j] + " " + entry.getKey()) || line.contains(rarityArrC[j] + " DUNGEON " + entry.getKey())) {
                                        allItemEnchs = entry.getValue().getAsJsonArray();
                                        break out;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch(Exception e) {}
        }

        boolean gotToEnchants = false;
        boolean passedEnchants = false;

        boolean dungeonProfit = false;
        int index = 0;
        List<String> newTooltip = new ArrayList<>();
        for(String line : event.toolTip) {
            if(line.contains("\u00A7cR\u00A76a\u00A7ei\u00A7an\u00A7bb\u00A79o\u00A7dw\u00A79 Rune")) {
                line = line.replace("\u00A7cR\u00A76a\u00A7ei\u00A7an\u00A7bb\u00A79o\u00A7dw\u00A79 Rune",
                        Utils.chromaString("Rainbow Rune", index, false)+EnumChatFormatting.BLUE);
            } else if(hasEnchantments) {
                if(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && neu.config.tooltipTweaks.missingEnchantList) {
                    boolean lineHasEnch = false;
                    for(String s : enchantIds) {
                        String enchantName = WordUtils.capitalizeFully(s.replace("_", " "));
                        if(line.contains(enchantName)) {
                            lineHasEnch = true;
                            break;
                        }
                    }
                    if(lineHasEnch) {
                        gotToEnchants = true;
                    } else {
                        if(gotToEnchants && !passedEnchants && Utils.cleanColour(line).trim().length() == 0) {
                            if(enchantsConst != null && allItemEnchs != null) {
                                List<String> missing = new ArrayList<>();
                                for(JsonElement enchIdElement : allItemEnchs) {
                                    String enchId = enchIdElement.getAsString();
                                    if(!enchId.startsWith("ultimate_") && !ignoreFromPool.contains(enchId) && !enchantIds.contains(enchId)) {
                                        missing.add(enchId);
                                    }
                                }
                                newTooltip.add("");
                                StringBuilder currentLine = new StringBuilder(EnumChatFormatting.RED+"Missing: "+EnumChatFormatting.GRAY);
                                for(int i=0; i<missing.size(); i++) {
                                    String enchName = WordUtils.capitalizeFully(missing.get(i).replace("_", " "));
                                    if(currentLine.length() != 0 && (Utils.cleanColour(currentLine.toString()).length() + enchName.length()) > 40) {
                                        newTooltip.add(currentLine.toString());
                                        currentLine = new StringBuilder();
                                    }
                                    if(currentLine.length() != 0 && i != 0) {
                                        currentLine.append(", ").append(enchName);
                                    } else {
                                        currentLine.append(EnumChatFormatting.GRAY).append(enchName);
                                    }
                                }
                                if(currentLine.length() != 0) {
                                    newTooltip.add(currentLine.toString());
                                }
                            }
                            passedEnchants = true;
                        }
                    }
                }
                for(String op : neu.config.hidden.enchantColours) {
                    List<String> colourOps = GuiEnchantColour.splitter.splitToList(op);
                    String enchantName = GuiEnchantColour.getColourOpIndex(colourOps, 0);
                    String comparator = GuiEnchantColour.getColourOpIndex(colourOps, 1);
                    String comparison = GuiEnchantColour.getColourOpIndex(colourOps, 2);
                    String colourCode = GuiEnchantColour.getColourOpIndex(colourOps, 3);

                    if(enchantName.length() == 0) continue;
                    if(comparator.length() == 0) continue;
                    if(comparison.length() == 0) continue;
                    if(colourCode.length() == 0) continue;

                    int comparatorI = ">=<".indexOf(comparator.charAt(0));

                    int levelToFind = -1;
                    try {
                        levelToFind = Integer.parseInt(comparison);
                    } catch(Exception e) { continue; }

                    if(comparatorI < 0) continue;
                    if("0123456789abcdefz".indexOf(colourCode.charAt(0)) < 0) continue;

                    //item_lore = item_lore.replaceAll("\\u00A79("+lvl4Max+" IV)", EnumChatFormatting.DARK_PURPLE+"$1");
                    //9([a-zA-Z ]+?) ([0-9]+|(I|II|III|IV|V|VI|VII|VIII|IX|X))(,|$)
                    Pattern pattern;
                    try {
                        pattern = Pattern.compile("(\\u00A79|\\u00A79\\u00A7d\\u00A7l)("+enchantName+") " +
                                "([0-9]+|(I|II|III|IV|V|VI|VII|VIII|IX|X|XI|XII|XIII|XIV|XV|XVI|XVII|XVIII|XIX|XX))(,|$)");
                    } catch(Exception e) {continue;} //malformed regex
                    Matcher matcher = pattern.matcher(line);
                    int matchCount = 0;
                    while(matcher.find() && matchCount < 5) {
                        if(Utils.cleanColour(matcher.group(2)).startsWith(" ")) continue;

                        matchCount++;
                        int level = -1;
                        String levelStr = matcher.group(matcher.groupCount()-2);
                        if(levelStr == null) continue;
                        try {
                            level = Integer.parseInt(levelStr);
                        } catch(Exception e) {
                            switch(levelStr) {
                                case "I":
                                    level = 1; break;
                                case "II":
                                    level = 2; break;
                                case "III":
                                    level = 3; break;
                                case "IV":
                                    level = 4; break;
                                case "V":
                                    level = 5; break;
                                case "VI":
                                    level = 6; break;
                                case "VII":
                                    level = 7; break;
                                case "VIII":
                                    level = 8; break;
                                case "IX":
                                    level = 9; break;
                                case "X":
                                    level = 10; break;
                                case "XI":
                                    level = 11; break;
                                case "XII":
                                    level = 12; break;
                                case "XIII":
                                    level = 13; break;
                                case "XIV":
                                    level = 14; break;
                                case "XV":
                                    level = 15; break;
                                case "XVI":
                                    level = 16; break;
                                case "XVII":
                                    level = 17; break;
                                case "XVIII":
                                    level = 18; break;
                                case "XIX":
                                    level = 19; break;
                                case "XX":
                                    level = 20; break;
                            }
                        }
                        boolean matches = false;
                        if(level > 0) {
                            switch(comparator) {
                                case ">":
                                    matches = level > levelToFind; break;
                                case "=":
                                    matches = level == levelToFind; break;
                                case "<":
                                    matches = level < levelToFind; break;
                            }
                        }
                        if(matches) {
                            if(!colourCode.equals("z")) {
                                line = line.replace("\u00A79"+matcher.group(2), "\u00A7"+colourCode+matcher.group(2));
                                line = line.replace("\u00A79\u00A7d\u00A7l"+matcher.group(2), "\u00A7"+colourCode+
                                        EnumChatFormatting.BOLD+matcher.group(2));
                            } else {
                                int offset = Minecraft.getMinecraft().fontRendererObj.getStringWidth(line.replaceAll(
                                        "\\u00A79"+matcher.group(2)+".*", ""));
                                line = line.replace("\u00A79"+matcher.group(2), Utils.chromaString(matcher.group(2), offset/12f+index, false));

                                offset = Minecraft.getMinecraft().fontRendererObj.getStringWidth(line.replaceAll(
                                        "\\u00A79\\u00A7d\\u00A7l"+matcher.group(2)+".*", ""));
                                line = line.replace("\u00A79\u00A7d\u00A7l"+matcher.group(2), Utils.chromaString(matcher.group(2),
                                        offset/12f+index, true));
                            }
                        }
                    }
                }
            }

            newTooltip.add(line);

            if(neu.config.tooltipTweaks.showPriceInfoAucItem) {
                if(line.contains(EnumChatFormatting.GRAY+"Buy it now: ") ||
                        line.contains(EnumChatFormatting.GRAY+"Bidder: ") ||
                        line.contains(EnumChatFormatting.GRAY+"Starting bid: ")) {

                    if(!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                        newTooltip.add("");
                        newTooltip.add(EnumChatFormatting.GRAY+"[SHIFT for Price Info]");
                    } else {
                        ItemPriceInformation.addToTooltip(newTooltip, internalname);
                    }
                }
            }

            if(neu.config.dungeonProfit.profitDisplayLoc == 2 && Minecraft.getMinecraft().currentScreen instanceof GuiChest) {
                if(line.contains(EnumChatFormatting.GREEN+"Open Reward Chest")) {
                    dungeonProfit = true;
                } else if(index == 7 && dungeonProfit) {
                    GuiChest eventGui = (GuiChest) Minecraft.getMinecraft().currentScreen;
                    ContainerChest cc = (ContainerChest) eventGui.inventorySlots;
                    IInventory lower = cc.getLowerChestInventory();

                    int chestCost = 0;
                    try {
                        String line6 = Utils.cleanColour(line);
                        StringBuilder cost = new StringBuilder();
                        for(int i=0; i<line6.length(); i++) {
                            char c = line6.charAt(i);
                            if("0123456789".indexOf(c) >= 0) {
                                cost.append(c);
                            }
                        }
                        if(cost.length() > 0) {
                            chestCost = Integer.parseInt(cost.toString());
                        }
                    } catch(Exception ignored) {}

                    String missingItem = null;
                    int totalValue = 0;
                    HashMap<String, Float> itemValues = new HashMap<>();
                    for(int i=0; i<5; i++) {
                        ItemStack item = lower.getStackInSlot(11+i);
                        String internal = neu.manager.getInternalNameForItem(item);
                        if(internal != null) {
                            internal = internal.replace("\u00CD", "I").replace("\u0130", "I");
                            float bazaarPrice = -1;
                            JsonObject bazaarInfo = neu.manager.auctionManager.getBazaarInfo(internal);
                            if(bazaarInfo != null && bazaarInfo.has("avg_sell")) {
                                bazaarPrice = bazaarInfo.get("avg_sell").getAsFloat();
                            }

                            float worth = -1;
                            if(bazaarPrice > 0) {
                                worth = bazaarPrice;
                            } else {
                                switch(neu.config.dungeonProfit.profitType) {
                                    case 1:
                                        worth = neu.manager.auctionManager.getItemAvgBin(internal);
                                        break;
                                    case 2:
                                        JsonObject auctionInfo = neu.manager.auctionManager.getItemAuctionInfo(internal);
                                        if(auctionInfo != null) {
                                            if(auctionInfo.has("clean_price")) {
                                                worth = (int)auctionInfo.get("clean_price").getAsFloat();
                                            } else {
                                                worth = (int)(auctionInfo.get("price").getAsFloat() / auctionInfo.get("count").getAsFloat());
                                            }
                                        }
                                        break;
                                    default:
                                        worth = neu.manager.auctionManager.getLowestBin(internal);
                                }
                                if(worth <= 0) {
                                    worth = neu.manager.auctionManager.getLowestBin(internal);
                                    if(worth <= 0) {
                                        worth = neu.manager.auctionManager.getItemAvgBin(internal);
                                        if(worth <= 0) {
                                            JsonObject auctionInfo = neu.manager.auctionManager.getItemAuctionInfo(internal);
                                            if(auctionInfo != null) {
                                                if(auctionInfo.has("clean_price")) {
                                                    worth = (int)auctionInfo.get("clean_price").getAsFloat();
                                                } else {
                                                    worth = (int)(auctionInfo.get("price").getAsFloat() / auctionInfo.get("count").getAsFloat());
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if(worth > 0 && totalValue >= 0) {
                                totalValue += worth;

                                String display = item.getDisplayName();

                                if(display.contains("Enchanted Book")) {
                                    NBTTagCompound tag = item.getTagCompound();
                                    if(tag != null && tag.hasKey("ExtraAttributes", 10)) {
                                        NBTTagCompound ea = tag.getCompoundTag("ExtraAttributes");
                                        NBTTagCompound enchants = ea.getCompoundTag("enchantments");

                                        int highestLevel = -1;
                                        for(String enchname : enchants.getKeySet()) {
                                            int level = enchants.getInteger(enchname);
                                            if(level > highestLevel) {
                                                display = EnumChatFormatting.BLUE+WordUtils.capitalizeFully(
                                                        enchname.replace("_", " ")
                                                                .replace("Ultimate", "")
                                                                .trim()) + " " + level;
                                            }
                                        }
                                    }
                                }

                                itemValues.put(display, worth);
                            } else {
                                if(totalValue != -1) {
                                    missingItem = internal;
                                }
                                totalValue = -1;
                            }
                        }
                    }

                    NumberFormat format = NumberFormat.getInstance(Locale.US);
                    String valueStringBIN1;
                    String valueStringBIN2;
                    if(totalValue >= 0) {
                        valueStringBIN1 = EnumChatFormatting.YELLOW+"Value (BIN): ";
                        valueStringBIN2 = EnumChatFormatting.GOLD + format.format(totalValue) + " coins";
                    } else {
                        valueStringBIN1 = EnumChatFormatting.YELLOW+"Can't find BIN: ";
                        valueStringBIN2 = missingItem;
                    }

                    int profitLossBIN = totalValue - chestCost;
                    String profitPrefix =  EnumChatFormatting.DARK_GREEN.toString();
                    String lossPrefix = EnumChatFormatting.RED.toString();
                    String prefix = profitLossBIN >= 0 ? profitPrefix : lossPrefix;

                    String plStringBIN;
                    if(profitLossBIN >= 0) {
                        plStringBIN = prefix + "+" + format.format(profitLossBIN) + " coins";
                    } else {
                        plStringBIN = prefix + "-"+ format.format(-profitLossBIN) + " coins";
                    }

                    String neu = EnumChatFormatting.YELLOW + "[NEU] ";

                    newTooltip.add(neu + valueStringBIN1 + " " + valueStringBIN2);
                    if(totalValue >= 0) {
                        newTooltip.add(neu + EnumChatFormatting.YELLOW+"Profit/Loss: " + plStringBIN);
                    }

                    for(Map.Entry<String, Float> entry : itemValues.entrySet()) {
                        newTooltip.add(neu + entry.getKey() + prefix+"+"+
                                format.format(entry.getValue().intValue()));
                    }
                }
            }

            index++;
        }

        event.toolTip.clear();
        event.toolTip.addAll(newTooltip);

        if(neu.config.tooltipTweaks.showPriceInfoInvItem) {
            ItemPriceInformation.addToTooltip(event.toolTip, internalname);
        }
    }

    /**
     * This makes it so that holding LCONTROL while hovering over an item with NBT will show the NBT of the item.
     * @param event
     */
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if(!neu.isOnSkyblock()) return;
        if(neu.config.improvedSBMenu.hideEmptyPanes &&
                event.itemStack.getItem().equals(Item.getItemFromBlock(Blocks.stained_glass_pane))) {
            String first = Utils.cleanColour(event.toolTip.get(0));
            first = first.replaceAll("\\(.*\\)", "").trim();
            if(first.length() == 0) {
                event.toolTip.clear();
            }
        }
        //AH prices
        /*if(Minecraft.getMinecraft().currentScreen != null) {
            if(Minecraft.getMinecraft().currentScreen instanceof GuiChest) {
                GuiChest chest = (GuiChest) Minecraft.getMinecraft().currentScreen;
                ContainerChest container = (ContainerChest) chest.inventorySlots;
                String containerName = container.getLowerChestInventory().getDisplayName().getUnformattedText();
                if(containerName.trim().equals("Auctions Browser")) {
                    String internalname = neu.manager.getInternalNameForItem(event.itemStack);
                    if(internalname != null) {
                        for(int i=0; i<event.toolTip.size(); i++) {
                            String line = event.toolTip.get(i);
                            if(line.contains(EnumChatFormatting.GRAY + "Bidder: ") ||
                                    line.contains(EnumChatFormatting.GRAY + "Starting bid: ") ||
                                    line.contains(EnumChatFormatting.GRAY + "Buy it now: ")) {
                                neu.manager.updatePrices();
                                JsonObject auctionInfo = neu.manager.getItemAuctionInfo(internalname);

                                if(auctionInfo != null) {
                                    NumberFormat format = NumberFormat.getInstance(Locale.US);
                                    int auctionPrice = (int)(auctionInfo.get("price").getAsFloat() / auctionInfo.get("count").getAsFloat());
                                    float costOfEnchants = neu.manager.getCostOfEnchants(internalname,
                                            event.itemStack.getTagCompound());
                                    int priceWithEnchants = auctionPrice+(int)costOfEnchants;

                                    event.toolTip.add(++i, EnumChatFormatting.GRAY + "Average price: " +
                                            EnumChatFormatting.GOLD + format.format(auctionPrice) + " coins");
                                    if(costOfEnchants > 0) {
                                        event.toolTip.add(++i, EnumChatFormatting.GRAY + "Average price (w/ enchants): " +
                                                EnumChatFormatting.GOLD +
                                                format.format(priceWithEnchants) + " coins");
                                    }

                                    if(neu.manager.config.advancedPriceInfo.value) {
                                        int salesVolume = (int) auctionInfo.get("sales").getAsFloat();
                                        int flipPrice = (int)(0.93*priceWithEnchants);

                                        event.toolTip.add(++i, EnumChatFormatting.GRAY + "Flip Price (93%): " +
                                                EnumChatFormatting.GOLD + format.format(flipPrice) + " coins");
                                        event.toolTip.add(++i, EnumChatFormatting.GRAY + "Volume: " +
                                                EnumChatFormatting.GOLD + format.format(salesVolume) + " sales/day");
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }*/
        if(!Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)/* || /*!neu.config.hidden.dev*/) return;
        if(event.toolTip.size()>0&&event.toolTip.get(event.toolTip.size()-1).startsWith(EnumChatFormatting.DARK_GRAY + "NBT: ")) {
            event.toolTip.remove(event.toolTip.size()-1);

            StringBuilder sb = new StringBuilder();
            String nbt = event.itemStack.getTagCompound().toString();
            int indent = 0;
            for(char c : nbt.toCharArray()) {
                boolean newline = false;
                if(c == '{' || c == '[') {
                    indent++;
                    newline = true;
                } else if(c == '}' || c == ']') {
                    indent--;
                    sb.append("\n");
                    for(int i=0; i<indent; i++) sb.append("  ");
                } else if(c == ',') {
                    newline = true;
                } else if(c == '\"') {
                    sb.append(EnumChatFormatting.RESET.toString() + EnumChatFormatting.GRAY);
                }

                sb.append(c);
                if(newline) {
                    sb.append("\n");
                    for(int i=0; i<indent; i++) sb.append("  ");
                }
            }
            event.toolTip.add(sb.toString());
            if(Keyboard.isKeyDown(Keyboard.KEY_H)) {
                StringSelection selection = new StringSelection(sb.toString());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            }
        }
    }
}
