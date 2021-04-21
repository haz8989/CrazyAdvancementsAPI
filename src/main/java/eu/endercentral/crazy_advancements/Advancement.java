package eu.endercentral.crazy_advancements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import eu.endercentral.crazy_advancements.AdvancementDisplay.AdvancementFrame;
import net.minecraft.server.v1_16_R3.*;
import net.minecraft.server.v1_16_R3.IChatBaseComponent.ChatSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Warning;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.*;

public class Advancement {

    private static final HashMap<String, Advancement> advancementMap = new HashMap<>();

    private transient NameKey name;
    @SerializedName("name")
    private final String nameKey;

    private final AdvancementDisplay display;
    private SaveMethod saveMethod = SaveMethod.DEFAULT;

    private transient Advancement parent;
    @SerializedName("parent")
    private final String parentKey;
    private transient HashSet<Advancement> children = new HashSet<>();

    @SerializedName("criteriaAmount")
    private int criteria = 1;


    private transient AdvancementReward reward;


    private transient Map<String, HashSet<String>> awardedCriteria = new HashMap<>();
    private transient Map<String, AdvancementProgress> progress = new HashMap<>();

    /**
     * Generates an Advancement
     *
     * @param json JSON representation of {@link Advancement} instance
     * @return Generated {@link Advancement}
     */
    public static Advancement fromJSON(String json) {
        Gson gson = new GsonBuilder().setLenient().create();
        Advancement created = gson.fromJson(json, Advancement.class);
        created.loadAfterGSON();
        return created;
    }

    /**
     * Generates an Advancement
     *
     * @param json JSON representation of {@link Advancement} instance
     * @return Generated {@link Advancement}
     */
    public static Advancement fromJSON(JsonElement json) {
        Gson gson = new GsonBuilder().setLenient().create();
        Advancement created = gson.fromJson(json, Advancement.class);
        created.loadAfterGSON();
        return created;
    }

    private void loadAfterGSON() {
        this.children = new HashSet<>();
        this.name = new NameKey(nameKey);
        advancementMap.put(nameKey, this);
        this.parent = advancementMap.get(parentKey);
        if (this.parent != null) this.parent.addChildren(this);

        this.display.setVisibility(AdvancementVisibility.parseVisibility(this.display.visibilityIdentifier));
    }

    private void addChildren(Advancement adv) {
        children.add(adv);
    }

    /**
     * @return JSON representation of current {@link Advancement} instance
     */
    public String getAdvancementJSON() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    /**
     * @param parent  Parent advancement, used for drawing lines between different advancements
     * @param name    Unique Name
     * @param display
     */
    public Advancement(@Nullable Advancement parent, NameKey name, AdvancementDisplay display) {
        this.parent = parent;
        if (this.parent != null) this.parent.addChildren(this);
        this.parentKey = parent == null ? null : parent.getName().toString();
        this.name = name;
        this.nameKey = name.toString();
        this.display = display;
    }

    /**
     * @return The parent Advancement
     */
    @Nullable
    public Advancement getParent() {
        return parent;
    }

    /**
     * Sets the Required Criteria Amount
     *
     * @param criteria
     */
    public void setCriteria(int criteria) {
        this.criteria = criteria;
        savedCriteria = null;
        savedCriterionNames = null;
        savedCriteriaRequirements = null;
    }

    /**
     * @return Required Criteria Amount
     */
    public int getCriteria() {
        return criteria;
    }

    /**
     * @return Unique Name
     */
    public NameKey getName() {
        return name;
    }

    /**
     * Get the Display
     *
     * @return The display
     */
    public AdvancementDisplay getDisplay() {
        return display;
    }

    /**
     * Set the Save/Load method<br>{@link SaveMethod#DEFAULT} - Criteria Values will be saved and loaded<br>{@link SaveMethod#NUMBER} - Criteria Number will be saved and loaded
     *
     * @param saveMethod The Save/Load Method
     */
    public void setSaveMethod(SaveMethod saveMethod) {
        this.saveMethod = saveMethod;
    }

    /**
     * Get the Save/Load Method that is currently in use
     *
     * @return The Save/Load Method
     */
    public SaveMethod getSaveMethod() {
        return saveMethod;
    }

    /**
     * Sets the Reward for completing the Advancement
     *
     * @param reward
     */
    public void setReward(@Nullable AdvancementReward reward) {
        this.reward = reward;
    }

    /**
     * @return Currently set Reward
     */
    public AdvancementReward getReward() {
        return reward;
    }

    /**
     * Displays an Advancement Message to every Player saying Player has completed said advancement<br>
     * Note that this doesn't grant the advancement
     *
     * @param player Player who has recieved the advancement
     */
    public void displayMessageToEverybody(Player player) {
        IChatBaseComponent message = getMessage(player);

        PacketPlayOutChat packet = new PacketPlayOutChat(message, ChatMessageType.CHAT, CrazyAdvancements.CHAT_MESSAGE_UUID);
        for (Player online : Bukkit.getOnlinePlayers()) {
            ((CraftPlayer) online).getHandle().playerConnection.sendPacket(packet);
        }
    }

    /**
     * @param player Player who has recieved the advancement
     * @return
     */
    public IChatBaseComponent getMessage(Player player) {
        String translation = "chat.type.advancement." + display.getFrame().name().toLowerCase();

        IChatBaseComponent title = ChatSerializer.a(display.getTitle().getJson());
        IChatBaseComponent description = ChatSerializer.a(display.getDescription().getJson());

        ChatModifier tm = title.getChatModifier();
        AdvancementFrame frame = getDisplay().getFrame();
        EnumChatFormat typeColor = frame == AdvancementFrame.CHALLENGE ? EnumChatFormat.DARK_PURPLE : EnumChatFormat.GREEN;
        String color = tm.getColor() == null ? typeColor.name().toLowerCase() : tm.getColor().name;

        return ChatSerializer.a("{"
                + "\"translate\":\"" + translation + "\","
                + "\"with\":"
                + "["
                + "\"" + player.getDisplayName() + "\","
                + "{"
                + "\"text\":\"[" + title.getText() + "]\",\"color\":\"" + color + "\",\"bold\":" + tm.isBold() + ",\"italic\":" + tm.isItalic() + ", \"strikethrough\":" + tm.isStrikethrough() + ",\"underlined\":" + tm.isUnderlined() + ",\"obfuscated\":" + tm.isRandom() + ","
                + "\"hoverEvent\":"
                + "{"
                + "\"action\":\"show_text\","
                + "\"value\":[\"\", {\"text\":\"" + title.getText() + "\",\"color\":\"" + color + "\",\"bold\":" + tm.isBold() + ",\"italic\":" + tm.isItalic() + ", \"strikethrough\":" + tm.isStrikethrough() + ",\"underlined\":" + tm.isUnderlined() + ",\"obfuscated\":" + tm.isRandom() + "}, {\"text\":\"\\n\"}, {\"text\":\"" + description.getText() + "\"}]"
                + "}"
                + "}"
                + "]"
                + "}");

    }

    /**
     * Sends a Toast Message regardless if the Player has it in one of their Advancement Managers or not
     *
     * @param player Player who should see the Toast Message
     */
    public void displayToast(Player player) {
        MinecraftKey notName = new MinecraftKey("eu.endercentral", "notification");

        AdvancementDisplay display = getDisplay();

        AdvancementRewards advRewards = new AdvancementRewards(0, new MinecraftKey[0], new MinecraftKey[0], null);
        ItemStack icon = CraftItemStack.asNMSCopy(display.getIcon());

        MinecraftKey backgroundTexture = null;
        boolean hasBackgroundTexture = display.getBackgroundTexture() != null;

        if (hasBackgroundTexture) {
            backgroundTexture = new MinecraftKey(display.getBackgroundTexture());
        }

        Map<String, Criterion> advCriteria = new HashMap<>();
        String[][] advRequirements = new String[][]{};

        advCriteria.put("for_free", new Criterion(new CriterionInstance() {
            @Override
            public JsonObject a(LootSerializationContext arg0) {
                return null;
            }

            @Override
            public MinecraftKey a() {
                return new MinecraftKey("minecraft", "impossible");
            }
        }));
        ArrayList<String[]> fixedRequirements = new ArrayList<>();

        fixedRequirements.add(new String[]{"for_free"});

        advRequirements = Arrays.stream(fixedRequirements.toArray()).toArray(String[][]::new);

        net.minecraft.server.v1_16_R3.AdvancementDisplay saveDisplay = new net.minecraft.server.v1_16_R3.AdvancementDisplay(icon, display.getTitle().getBaseComponent(), display.getDescription().getBaseComponent(), backgroundTexture, display.getFrame().getNMS(), true, display.isAnnouncedToChat(), true);
        net.minecraft.server.v1_16_R3.Advancement saveAdv = new net.minecraft.server.v1_16_R3.Advancement(notName, getParent() == null ? null : getParent().getSavedAdvancement(), saveDisplay, advRewards, advCriteria, advRequirements);


        HashMap<MinecraftKey, AdvancementProgress> prg = new HashMap<>();

        AdvancementProgress advPrg = new AdvancementProgress();
        advPrg.a(advCriteria, advRequirements);
        advPrg.getCriterionProgress("for_free").b();
        prg.put(notName, advPrg);

        PacketPlayOutAdvancements packet = new PacketPlayOutAdvancements(false, Arrays.asList(saveAdv), new HashSet<>(), prg);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);


        HashSet<MinecraftKey> rm = new HashSet<>();
        rm.add(notName);
        prg.clear();
        packet = new PacketPlayOutAdvancements(false, new ArrayList<>(), rm, prg);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    /**
     * @return All direct children
     */
    public HashSet<Advancement> getChildren() {
        return (HashSet<Advancement>) children.clone();
    }


    //Advancement Row

    /**
     * @return Root {@link Advancement}
     */
    public Advancement getRootAdvancement() {
        if (parent == null) {
            return this;
        } else {
            return parent.getRootAdvancement();
        }
    }

    /**
     * @return Unique Name of Advancement Tab
     */
    public NameKey getTab() {
        return getRootAdvancement().getName();
    }

    /**
     * @return All parents and children
     */
    public List<Advancement> getRow() {
        List<Advancement> row = new ArrayList<>();
        row.add(this);
        if (getParent() != null) {
            for (Advancement untilRow : getParent().getRowUntil()) {
                if (!row.contains(untilRow)) row.add(untilRow);
            }
            Collections.reverse(row);
        }
        for (Advancement child : getChildren()) {
            for (Advancement afterRow : child.getRowAfter()) {
                if (!row.contains(afterRow)) row.add(afterRow);
            }
        }
        return row;
    }

    /**
     * @return All parents
     */
    public List<Advancement> getRowUntil() {
        List<Advancement> row = new ArrayList<>();
        row.add(this);
        if (getParent() != null) {
            for (Advancement untilRow : getParent().getRowUntil()) {
                if (!row.contains(untilRow)) row.add(untilRow);
            }
        }
        return row;
    }

    /**
     * @return All children
     */
    public List<Advancement> getRowAfter() {
        List<Advancement> row = new ArrayList<>();
        row.add(this);
        for (Advancement child : getChildren()) {
            for (Advancement afterRow : child.getRowAfter()) {
                if (!row.contains(afterRow)) row.add(afterRow);
            }
        }
        return row;
    }

    /**
     * @param player Player to check
     * @return true if any parent is granted
     */
    public boolean isAnythingGrantedUntil(Player player) {
        for (Advancement until : getRowUntil()) {
            if (until.isGranted(player)) return true;
        }
        return false;
    }

    /**
     * @param player Player to check
     * @return true if any child is granted
     */
    public boolean isAnythingGrantedAfter(Player player) {
        for (Advancement after : getRowAfter()) {
            if (after.isGranted(player)) return true;
        }
        return false;
    }

//	public boolean isAnythingGrantedUntil(AdvancementManager manager, Player player) {
//		for(Advancement until : getRowUntil()) {
//			if(manager.getCriteriaProgress(player, until) >= getCriteria()) return true;
//		}
//		return false;
//	}
//	
//	public boolean isAnythingGrantedAfter(AdvancementManager manager, Player player) {
//		for(Advancement after : getRowAfter()) {
//			if(manager.getCriteriaProgress(player, after) >= getCriteria()) return true;
//		}
//		return false;
//	}


    //Saved

    private transient Map<String, Criterion> savedCriteria = null;
    @SerializedName("criteria")
    private Set<String> savedCriterionNames = null;
    @SerializedName("criteriaRequirements")
    private String[][] savedCriteriaRequirements = null;
    private transient net.minecraft.server.v1_16_R3.Advancement savedAdvancement = null;

    private transient HashMap<String, Boolean> savedHiddenStatus;

    @Warning(reason = "Only use if you know what you are doing!")
    public void saveHiddenStatus(Player player, boolean hidden) {
        if (savedHiddenStatus == null) savedHiddenStatus = new HashMap<>();
        savedHiddenStatus.put(player.getUniqueId().toString(), hidden);
    }

    public boolean getHiddenStatus(Player player) {
        if (savedHiddenStatus == null) savedHiddenStatus = new HashMap<>();
        if (!savedHiddenStatus.containsKey(player.getUniqueId().toString()))
            savedHiddenStatus.put(player.getUniqueId().toString(), getDisplay().isVisible(player, this));
        return savedHiddenStatus.get(player.getUniqueId().toString());
    }


    @Warning(reason = "Only use if you know what you are doing!")
    public void saveCriteria(Map<String, Criterion> save) {
        savedCriteria = save;
        savedCriterionNames = save.keySet();
    }

    public Map<String, Criterion> getSavedCriteria() {
        return savedCriteria;
    }

    @Warning(reason = "Only use if you know what you are doing!")
    public void saveCriteriaRequirements(String[][] save) {
        savedCriteriaRequirements = save;
    }

    public String[][] getSavedCriteriaRequirements() {
        return savedCriteriaRequirements;
    }

    @Warning(reason = "Unsafe")
    public void saveAdvancement(net.minecraft.server.v1_16_R3.Advancement save) {
        savedAdvancement = save;
    }

    public net.minecraft.server.v1_16_R3.Advancement getSavedAdvancement() {
        return savedAdvancement;
    }


    //Player Actions

    public HashSet<String> getAwardedCriteria(UUID uuid) {
        if (!getAwardedCriteria().containsKey(uuid.toString()))
            getAwardedCriteria().put(uuid.toString(), new HashSet<>());
        return getAwardedCriteria().get(uuid.toString());
    }

    public Map<String, HashSet<String>> getAwardedCriteria() {
        if (awardedCriteria == null) awardedCriteria = new HashMap<>();
        return awardedCriteria;
    }

    @Warning(reason = "Unsafe")
    public void setAwardedCriteria(Map<String, HashSet<String>> awardedCriteria) {
        this.awardedCriteria = awardedCriteria;
    }

    @Warning(reason = "Only use if you know what you are doing!")
    public void unsetAwardedCriteria(UUID uuid) {
        if (this.awardedCriteria == null) this.awardedCriteria = new HashMap<>();
        this.awardedCriteria.remove(uuid.toString());
    }

    public AdvancementProgress getProgress(Player player) {
        if (this.progress == null) progress = new HashMap<>();
        return this.progress.containsKey(player.getUniqueId().toString()) ? this.progress.get(player.getUniqueId().toString()) : new AdvancementProgress();
    }

    public AdvancementProgress getProgress(UUID uuid) {
        if (this.progress == null) progress = new HashMap<>();
        return this.progress.containsKey(uuid.toString()) ? this.progress.get(uuid.toString()) : new AdvancementProgress();
    }

    @Warning(reason = "Only use if you know what you are doing!")
    public void setProgress(Player player, AdvancementProgress progress) {
        if (this.progress == null) this.progress = new HashMap<>();
        this.progress.put(player.getUniqueId().toString(), progress);
    }

    @Warning(reason = "Only use if you know what you are doing!")
    public void unsetProgress(UUID uuid) {
        if (this.progress == null) this.progress = new HashMap<>();
        this.progress.remove(uuid.toString());
    }

    public boolean isDone(Player player) {
        return getProgress(player).isDone();
    }

    public boolean isDone(UUID uuid) {
        return getProgress(uuid).isDone();
    }

    /**
     * @param player Player to check
     * @return true if advancement is granted
     */
    public boolean isGranted(Player player) {
        return getProgress(player).isDone();
    }


    /**
     * @param key Key to check
     * @return true if {@link Advancement} name and key share the same namespace and name
     */
    public boolean hasName(NameKey key) {
        return key.getNamespace().equalsIgnoreCase(name.getNamespace()) && key.getKey().equalsIgnoreCase(name.getKey());
    }

    @Override
    public String toString() {
        return "Advancement " + getAdvancementJSON() + "";
    }

}