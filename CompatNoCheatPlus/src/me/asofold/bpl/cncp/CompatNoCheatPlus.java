package me.asofold.bpl.cncp;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import me.asofold.bpl.cncp.config.compatlayer.CompatConfig;
import me.asofold.bpl.cncp.config.compatlayer.NewConfig;
import me.asofold.bpl.cncp.hooks.Hook;
import me.asofold.bpl.cncp.hooks.generic.HookPlayerClass;
import me.asofold.bpl.cncp.hooks.ncp.NCPHook;
import me.asofold.bpl.cncp.hooks.ncp.NCPHookManager;
import me.asofold.bpl.cncp.setttings.Settings;
import me.asofold.bpl.cncp.utils.Utils;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import fr.neatmonster.nocheatplus.checks.CheckEvent;
import fr.neatmonster.nocheatplus.checks.blockbreak.Direction.DirectionEvent;
import fr.neatmonster.nocheatplus.checks.blockbreak.FastBreak.FastBreakEvent;
import fr.neatmonster.nocheatplus.checks.blockbreak.NoSwing.NoSwingEvent;
import fr.neatmonster.nocheatplus.checks.fight.Angle.AngleEvent;
import fr.neatmonster.nocheatplus.checks.fight.Speed.SpeedEvent;
import fr.neatmonster.nocheatplus.checks.moving.CreativeFly.CreativeFlyEvent;
import fr.neatmonster.nocheatplus.checks.moving.NoFall.NoFallEvent;
import fr.neatmonster.nocheatplus.checks.moving.SurvivalFly.SurvivalFlyEvent;

/**
 * Quick attempt to provide compatibility to NoCheatPlus (by NeatMonster) for some other plugins that change the vanilla game mechanichs, for instance by fast block breaking. 
 * @author mc_dev
 *
 */
public class CompatNoCheatPlus extends JavaPlugin implements Listener {
	
	
	private final Settings settings = new Settings();
	
	private final HookPlayerClass hookPlayerClass = new HookPlayerClass();
	
	/**
	 * Flag if plugin is enabled.
	 */
	private static boolean enabled = false;
	
	/**
	 * Experimental: static method to enable this plugin, only enables if it is not already enabled.
	 * @return
	 */
	public static boolean enableCncp(){
		if (enabled) return true;
		return enablePlugin("CompatNoCheatPlus");
	}
	
	/**
	 * Static method to enable a plugin (might also be useful for hooks).
	 * @param plgName
	 * @return
	 */
	public static boolean enablePlugin(String plgName) {
		PluginManager pm = Bukkit.getPluginManager();
		Plugin plugin = pm.getPlugin(plgName);
		if (plugin == null) return false;
		if (pm.isPluginEnabled(plugin)) return true;
		pm.enablePlugin(plugin);
		return true;
	}
	
	/**
	 * Static method to disable a plugin (might also be useful for hooks).
	 * @param plgName
	 * @return
	 */
	public static boolean disablePlugin(String plgName){
		PluginManager pm = Bukkit.getPluginManager();
		Plugin plugin = pm.getPlugin(plgName);
		if (plugin == null) return false;
		if (!pm.isPluginEnabled(plugin)) return true;
		pm.disablePlugin(plugin);
		return true;
	}

	/**
	 * API to add a hook. Adds the hook AND registers listeners if enabled. Also respects the configuration for preventing hooks.<br>
	 * If you want to not register the listeners use NCPHookManager.
	 * @param hook
	 * @return
	 */
	public static boolean addHook(Hook hook){
		if (Settings.preventAddHooks.contains(hook.getHookName())){
			System.out.println("[cncp] Prevented adding hook: "+hook.getHookName() + " / " + hook.getHookVersion());
			return false;
		}
		if (enabled) registerListeners(hook);
		Integer[] checkIds = hook.getCheckSpec();
		NCPHookManager.addHook(checkIds, hook); // This logs the message.
		return true;
	}
	
	/**
	 * Conveniently register the listeners, do not use if you add/added the hook with addHook. 
	 * @param hook
	 * @return
	 */
	public  static boolean registerListeners(Hook hook) {
		if (!enabled) return false;
		Listener[] listeners = hook.getListeners();
		if (listeners != null){
			// attempt to register events:
			PluginManager pm = Bukkit.getPluginManager();
			Plugin plg = pm.getPlugin("CompatNoCheatPlus");
			if (plg == null) return false;
			for (Listener listener : listeners) {
				pm.registerEvents(listener, plg);
			}
		}
		return true;
	}

	/**
	 * Add standard hooks if available.
	 */
	private void addAvailableHooks() {
		addHook(hookPlayerClass);
		try{
			addHook(new me.asofold.bpl.cncp.hooks.mcmmo.HookmcMMO());
		}
		catch (Throwable t){}
	}
	
	@Override
	public void onEnable() {
		enabled = false; // make sure
		// (no cleanup)
		
		// Settings:
		settings.clear();
		reloadSettings();
		// Register own listener:
		final PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(this, this);
		super.onEnable();
		
		// Add  Hooks:
		addAvailableHooks(); // add before enable is set to not yet register listeners.
		enabled = true;
		
		// register all listeners:
		for (Hook hook : getAllHooks()){
			registerListeners(hook);
		}
	}
	
	/**
	 * Get all cncp Hook instances that are registered with NCPHookManager.
	 * @return
	 */
	public static Collection<Hook> getAllHooks() {
		List<Hook> hooks = new LinkedList<Hook>();
		for (NCPHook hook : NCPHookManager.getAllHooks()){
			if (hook instanceof Hook) hooks.add((Hook) hook);
		}
		return hooks;
	}

	public boolean reloadSettings() {
		final Set<String> oldForceEnableLater = new LinkedHashSet<String>();
		oldForceEnableLater.addAll(settings.forceEnableLater);
		// Read and apply config to settings:
		File file = new File(getDataFolder() , "cncp.yml");
		CompatConfig cfg = new NewConfig(file);
		cfg.load();
		if (Settings.addDefaults(cfg)) cfg.save();
		settings.fromConfig(cfg);
		// Set hookPlayerClass properties
		hookPlayerClass.setClassNames(settings.exemptPlayerClassNames);
		hookPlayerClass.setExemptAll(settings.exemptAllPlayerClassNames);
		hookPlayerClass.setPlayerClassName(settings.playerClassName);
		hookPlayerClass.setCheckSuperClass(settings.exemptSuperClass);
		// Re-enable plugins that were not yet on the list:
		Server server = getServer();
		Logger logger = server.getLogger();
		for (String plgName : settings.loadPlugins){
			try{
				if (CompatNoCheatPlus.enablePlugin(plgName)){
					System.out.println("[cncp] Ensured that the following plugin is enabled: " + plgName);
				}
			}
			catch (Throwable t){
				logger.severe("[cncp] Failed to enable the plugin: " + plgName);
				logger.severe(Utils.toString(t));
			}
		}
		BukkitScheduler sched = server.getScheduler();
		for (String plgName : settings.forceEnableLater){
			if (!oldForceEnableLater.remove(plgName)) oldForceEnableLater.add(plgName);
		}
		if (!oldForceEnableLater.isEmpty()){
			System.out.println("[cncp] Schedule task to re-enable plugins later...");
			sched.scheduleSyncDelayedTask(this, new Runnable() {
				@Override
				public void run() {
					// (Later maybe re-enabling this plugin could be added.)
					// TODO: log levels !
					for (String plgName : oldForceEnableLater){
						try{
							if (disablePlugin(plgName)){
								if (enablePlugin(plgName)) System.out.println("[cncp] Re-enabled plugin: " + plgName);
								else System.out.println("[cncp] Could not re-enable plugin: "+plgName);
							}
							else{
								System.out.println("[cncp] Could not disable plugin (already disabled?): "+plgName);
							}
						}
						catch(Throwable t){
							// TODO: maybe log ?
						}
					}
				}
			}); 
		}
		
		return true;
	}

	@Override
	public void onDisable() {
		enabled = false;
		// remove all registered cncp hooks:
		for (Hook hook : getAllHooks()){
			NCPHookManager.removeHook(hook);
		}
		super.onDisable();
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled=true)
	final void onCheckFail(final CheckEvent event){
		
		// TODO: This will be replaced by NCP invoking NCPHookManager.should... directly.
		
		final Integer checkId;
		
		// horrible :) 
		if (event instanceof SurvivalFlyEvent) checkId = NCPHookManager.MOVING_SURVIVALFLY;
		else if (event instanceof CreativeFlyEvent) checkId = NCPHookManager.MOVING_CREATIVEFLY;
		else if (event instanceof NoFallEvent) checkId = NCPHookManager.MOVING_NOFALL;
		else if (event instanceof FastBreakEvent) checkId = NCPHookManager.BLOCKBREAK_FASTBREAK;
		else if (event instanceof NoSwingEvent) checkId = NCPHookManager.BLOCKBREAK_NOSWING;
		else if (event instanceof DirectionEvent) checkId = NCPHookManager.BLOCKBREAK_DIRECTION;
		else if (event instanceof SpeedEvent) checkId = NCPHookManager.FIGHT_SPEED;
		else if (event instanceof AngleEvent) checkId = NCPHookManager.FIGHT_ANGLE;
		else checkId = NCPHookManager.UNKNOWN;
		
		if (NCPHookManager.shouldCancelVLProcessing(checkId, event.getPlayer())) event.setCancelled(true);
	}

}
