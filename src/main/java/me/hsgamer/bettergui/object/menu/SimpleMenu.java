package me.hsgamer.bettergui.object.menu;

import static me.hsgamer.bettergui.BetterGUI.getInstance;

import co.aikar.taskchain.TaskChain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import me.hsgamer.bettergui.BetterGUI;
import me.hsgamer.bettergui.builder.CommandBuilder;
import me.hsgamer.bettergui.builder.IconBuilder;
import me.hsgamer.bettergui.config.impl.MessageConfig.DefaultMessage;
import me.hsgamer.bettergui.manager.VariableManager;
import me.hsgamer.bettergui.object.Command;
import me.hsgamer.bettergui.object.Icon;
import me.hsgamer.bettergui.object.Menu;
import me.hsgamer.bettergui.object.ParentIcon;
import me.hsgamer.bettergui.object.inventory.SimpleInventory;
import me.hsgamer.bettergui.util.CaseInsensitiveStringMap;
import me.hsgamer.bettergui.util.CommonUtils;
import me.hsgamer.bettergui.util.TestCase;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.permissions.Permission;

public class SimpleMenu extends Menu {

  private final Map<Integer, Icon> icons = new HashMap<>();
  private final List<Command> openActions = new ArrayList<>();
  private final List<Command> closeActions = new ArrayList<>();
  private InventoryType inventoryType = InventoryType.CHEST;
  private String title;
  private boolean titleHasVariable = false;
  private int maxSlots = 27;
  private long ticks = 0;
  private Permission permission = new Permission(
      getInstance().getName().toLowerCase() + "." + getName());
  private Icon defaultIcon;

  public SimpleMenu(String name) {
    super(name);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void setFromFile(FileConfiguration file) {
    for (String key : file.getKeys(false)) {
      if (key.equalsIgnoreCase("menu-settings")) {
        Map<String, Object> keys = new CaseInsensitiveStringMap<>(
            file.getConfigurationSection(key).getValues(false));

        if (keys.containsKey(Settings.NAME)) {
          title = (String) keys.get(Settings.NAME);
          titleHasVariable = VariableManager.hasVariables(title);
        }

        if (keys.containsKey(Settings.INVENTORY_TYPE)) {
          try {
            inventoryType = InventoryType.valueOf((String) keys.get(Settings.INVENTORY_TYPE));
          } catch (IllegalArgumentException e) {
            getInstance().getLogger().log(Level.WARNING, "The menu \"" + file.getName()
                + "\" contains an illegal inventory type, it will be CHEST by default");
          }
          switch (inventoryType) {
            case FURNACE:
            case ENDER_CHEST:
            case CHEST:
            case HOPPER:
            case WORKBENCH:
            case DISPENSER:
            case DROPPER:
              maxSlots = inventoryType.getDefaultSize();
              break;
            default:
              inventoryType = InventoryType.CHEST;
              getInstance().getLogger().log(Level.WARNING, "The menu \"" + file.getName()
                  + "\"'s inventory type is not supported, it will be CHEST by default");
          }
        } else if (keys.containsKey(Settings.ROWS)) {
          int temp = (int) keys.get(Settings.ROWS) * 9;
          maxSlots = temp > 0 ? temp : maxSlots;
        }

        if (keys.containsKey(Settings.COMMAND)) {
          Object value = keys.get(Settings.COMMAND);
          List<String> commands = new ArrayList<>();
          if (value instanceof List) {
            commands = (List<String>) value;
          } else if (value instanceof String) {
            commands = Arrays.asList(((String) value).split(";"));
          }
          commands.replaceAll(String::trim);
          commands.forEach(s -> getInstance().getCommandManager().registerMenuCommand(s, this));
        }

        if (keys.containsKey(Settings.OPEN_ACTION)) {
          openActions.addAll(
              CommandBuilder.getCommands(null, (List<String>) keys.get(Settings.OPEN_ACTION)));
        }
        if (keys.containsKey(Settings.CLOSE_ACTION)) {
          closeActions.addAll(
              CommandBuilder.getCommands(null, (List<String>) keys.get(Settings.CLOSE_ACTION)));
        }

        if (keys.containsKey(Settings.PERMISSION)) {
          permission = new Permission((String) keys.get(Settings.PERMISSION));
        }

        if (keys.containsKey(Settings.AUTO_REFRESH)) {
          ticks = (int) keys.get(Settings.AUTO_REFRESH);
        }
      } else if (key.equalsIgnoreCase("default-icon")) {
        defaultIcon = IconBuilder.getIcon(this, file.getConfigurationSection(key));
      } else {
        ConfigurationSection section = file.getConfigurationSection(key);
        Icon icon = IconBuilder.getIcon(this, section);
        List<Integer> slots = IconBuilder.getSlots(section);
        for (Integer slot : slots) {
          if (icons.containsKey(slot)) {
            Icon tempIcon = icons.get(slot);
            if (tempIcon instanceof ParentIcon) {
              ((ParentIcon) tempIcon).addChild(icon.cloneIcon());
            } else {
              getInstance().getLogger().warning(
                  icon.getName() + " & " + tempIcon.getName() + " from " + getName()
                      + " have the same slot. Only one of them will be set");
            }
          } else {
            if (slot < maxSlots) {
              icons.put(slot, icon.cloneIcon());
            } else {
              getInstance().getLogger().warning(
                  icon.getName() + " from " + getName() + " has invalid slot (Exceed the limit)");
            }
          }
        }
      }
    }
  }

  @Override
  public void createInventory(Player player) {
    TestCase.create(player)
        .setPredicate(player1 -> player1.hasPermission(permission))
        .setSuccessConsumer(player1 -> {
          final SimpleInventory[] inventory = new SimpleInventory[1];
          String parsedTitle = CommonUtils
              .colorize(titleHasVariable ? VariableManager.setVariables(title, player)
                  : title);
          TestCase.create(inventoryType)
              .setPredicate(inventoryType1 -> inventoryType1.equals(InventoryType.CHEST))
              .setSuccessConsumer(inventoryType1 -> {
                if (parsedTitle != null) {
                  inventory[0] = new SimpleInventory(player, maxSlots, parsedTitle, icons,
                      defaultIcon, ticks);
                } else {
                  inventory[0] = new SimpleInventory(player, maxSlots, icons, defaultIcon, ticks);
                }
              })
              .setFailConsumer(inventoryType1 -> {
                if (parsedTitle != null) {
                  inventory[0] = new SimpleInventory(player, inventoryType1, maxSlots, parsedTitle,
                      icons,
                      defaultIcon,
                      ticks);
                } else {
                  inventory[0] = new SimpleInventory(player, inventoryType1, maxSlots, icons,
                      defaultIcon,
                      ticks);
                }
              })
              .test();

          if (!openActions.isEmpty()) {
            inventory[0].addOpenHandler(event -> {
              TaskChain<?> taskChain = BetterGUI.newChain();
              openActions.forEach(action -> action.addToTaskChain(player, taskChain));
              taskChain.execute();
            });
          }
          if (!closeActions.isEmpty()) {
            inventory[0].addCloseHandler(event -> {
              TaskChain<?> taskChain = BetterGUI.newChain();
              closeActions.forEach(action -> action.addToTaskChain(player, taskChain));
              taskChain.execute();
            });
          }
          inventory[0].open();
        })
        .setFailConsumer(player1 -> CommonUtils
            .sendMessage(player1,
                getInstance().getMessageConfig().get(DefaultMessage.NO_PERMISSION)))
        .test();
  }

  public Icon getDefaultIcon() {
    return defaultIcon;
  }

  private static class Settings {

    static final String NAME = "name";
    static final String ROWS = "rows";
    static final String INVENTORY_TYPE = "inventory-type";
    static final String COMMAND = "command";
    static final String OPEN_ACTION = "open-action";
    static final String CLOSE_ACTION = "close-action";
    static final String PERMISSION = "permission";
    static final String AUTO_REFRESH = "auto-refresh";
  }
}
