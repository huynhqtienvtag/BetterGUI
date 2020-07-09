package me.hsgamer.bettergui.manager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import me.hsgamer.bettergui.BetterGUI;
import me.hsgamer.bettergui.Permissions;
import me.hsgamer.bettergui.config.impl.MessageConfig;
import me.hsgamer.bettergui.object.Menu;
import me.hsgamer.bettergui.util.CommonUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandManager {

  private final Map<String, Command> registered = new HashMap<>();
  private final Map<String, Command> registeredMenuCommand = new HashMap<>();
  private final JavaPlugin plugin;
  private final Field knownCommandsField;
  private final CommandMap bukkitCommandMap;
  private Method syncCommandsMethod;

  public CommandManager(JavaPlugin plugin) {
    this.plugin = plugin;
    try {
      Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
      commandMapField.setAccessible(true);
      bukkitCommandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

      knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
      knownCommandsField.setAccessible(true);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }

    try {
      Class<?> craftServer = Class.forName("org.bukkit.craftbukkit."
          + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3]
          + ".CraftServer");
      syncCommandsMethod = craftServer.getDeclaredMethod("syncCommands");
    } catch (Exception e) {
      // Ignored
    }
    if (syncCommandsMethod != null) {
      syncCommandsMethod.setAccessible(true);
    }
  }

  /**
   * Register the command
   *
   * @param command the command object
   */
  public void register(Command command) {
    String name = command.getLabel();
    if (registered.containsKey(name)) {
      plugin.getLogger().log(Level.WARNING, "Duplicated \"{0}\" command ! Ignored", name);
      return;
    }

    bukkitCommandMap.register(plugin.getName(), command);
    registered.put(name, command);
  }

  /**
   * Unregister the command
   *
   * @param command the command object
   */
  public void unregister(Command command) {
    try {
      Map<?, ?> knownCommands = (Map<?, ?>) knownCommandsField.get(bukkitCommandMap);

      knownCommands.values().removeIf(command::equals);

      command.unregister(bukkitCommandMap);
      registered.remove(command.getLabel());
    } catch (ReflectiveOperationException e) {
      plugin.getLogger()
          .log(Level.WARNING, "Something wrong when unregister the command", e);
    }
  }

  /**
   * Unregister the command
   *
   * @param command the command label
   */
  public void unregister(String command) {
    if (registered.containsKey(command)) {
      unregister(registered.remove(command));
    }
  }

  /**
   * Register the command that opens the menu
   *
   * @param command the name of the command
   * @param menu    the menu
   */
  public void registerMenuCommand(String command, Menu<?> menu) {
    registerMenuCommand(new BukkitCommand(command) {
      @Override
      public boolean execute(CommandSender commandSender, String s, String[] strings) {
        if (commandSender instanceof Player) {
          menu.createInventory((Player) commandSender, strings,
              commandSender.hasPermission(Permissions.OPEN_MENU_BYPASS));
        } else {
          CommonUtils.sendMessage(commandSender, MessageConfig.PLAYER_ONLY.getValue());
        }
        return true;
      }
    });
  }

  /**
   * Register the command that opens the menu
   *
   * @param command the menu command
   */
  public void registerMenuCommand(Command command) {
    String name = command.getName();
    if (registeredMenuCommand.containsKey(name)) {
      plugin.getLogger().log(Level.WARNING, "Duplicated \"{0}\" command ! Ignored", name);
      return;
    }
    bukkitCommandMap.register(plugin.getName() + "_menu", command);
    registeredMenuCommand.put(name, command);
  }


  /**
   * Sync the commands to the server. Mainly used to make tab completer work in 1.13+
   */
  public void syncCommand() {
    if (syncCommandsMethod == null) {
      return;
    }

    try {
      syncCommandsMethod.invoke(BetterGUI.getInstance().getServer());
    } catch (IllegalAccessException | InvocationTargetException e) {
      BetterGUI.getInstance().getLogger().log(Level.WARNING, "Error when syncing commands", e);
    }
  }

  public void clearMenuCommand() {
    registeredMenuCommand.values().forEach(this::unregister);
    registeredMenuCommand.clear();
  }

  public Map<String, Command> getRegistered() {
    return registered;
  }

  public Map<String, Command> getRegisteredMenuCommand() {
    return registeredMenuCommand;
  }
}
