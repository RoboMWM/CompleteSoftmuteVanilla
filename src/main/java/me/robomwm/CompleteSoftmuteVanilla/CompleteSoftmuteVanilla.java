package me.robomwm.CompleteSoftmuteVanilla;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Created by Robo on 4/23/2016.
 */
public class CompleteSoftmuteVanilla extends JavaPlugin implements Listener
{
    GriefPrevention gp;
    DataStore ds;
    @Override
    public void onEnable()
    {
        gp = (GriefPrevention)getServer().getPluginManager().getPlugin("GriefPrevention");
        ds = gp.dataStore;
        getServer().getPluginManager().registerEvents(this, this);
    }

    boolean unCanceled = false;

    /*
    * Everything we check for is what GP cancels
    * Thus, we use this listener to see if GP canceled this event
    * If GP canceled it, we uncancel it and track the change
    * Our second command listener will only be called if this event is still uncanceled
    * in case other plugins want to cancel this event for whatever reason
    */
    @EventHandler(priority = EventPriority.LOWEST)
    void checkGPCancel(PlayerCommandPreprocessEvent event)
    {
        if (event.isCancelled())
        {
            unCanceled = true;
            event.setCancelled(false);
        }
        else
            unCanceled = false;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event)
    {
        if (unCanceled)
            return; //Everything we check for is what GP cancels - if we didn't uncancel, we have no business to do here

        event.setCancelled(true); //reset cancel status to true (after all we did uncancel)

        String message = event.getMessage();
        String [] args = message.split(" ");
        String command = args[0].toLowerCase();

        // Is command a valid whisper?
        if ((gp.config_eavesdrop_whisperCommands.contains(command) || command.equals("/minecraft:me")) && args.length > 2)
        {
            Player sender = event.getPlayer();

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null)
                return; //Shouldn't happen, but just in case

            //first check if players in question are ignoring each other, and respond appropriately.
            if (!sendSoftIgnore(sender, target, args))
            {
                //Otherwise, check if softmuted
                if (ds.isSoftMuted(sender.getUniqueId()))
                    sender.sendMessage(softMessage(target.getName(), args));
            }
        }
        // Is it an action message?
        else if (command.equals("/me") || command.equals("/minecraft:me"))
        {
            Player sender = event.getPlayer();
            if (ds.isSoftMuted(sender.getUniqueId()))
            {
                StringBuilder softMeBuilder = new StringBuilder();
                softMeBuilder.append("* " + sender.getName());
                for (int i = 2; i < args.length; i++)
                {
                    softMeBuilder.append(" ");
                    softMeBuilder.append(args[i]);
                }
                sender.sendMessage(softMeBuilder.toString());
            }
        }
    }

    //Deny softmuted players from adding text to signs
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onSignChange(SignChangeEvent event)
    {
        if (ds.isSoftMuted(event.getPlayer().getUniqueId()))
        {
            event.setCancelled(true); //TODO: tell online admins about this. Alternatively, adjust event priority
            this.getLogger().info("Denied " + event.getPlayer().getName() + " from placing text on a sign");
        }
    }

    /* HELPER METHODS
     * Idk how to javadocs
     * This method tells you if a player is ignoring another player
     * Returns 0 if neither player is ignoring
     * Returns 1 if the target is ignoring the sender, or if players were administratively separated
     * Returns 2 if the sender is ignoring the target
     */
    public int isIgnored(Player sender, Player target)
    {
        PlayerData playerData = ds.getPlayerData(target.getPlayer().getUniqueId());
        if (playerData.ignoredPlayers.containsKey(sender.getUniqueId()))
            return 1; //target ignoring sender
        playerData = ds.getPlayerData(sender.getPlayer().getUniqueId());
        if (playerData.ignoredPlayers.containsKey(target.getUniqueId()))
        {
            if (playerData.ignoredPlayers.get(target.getUniqueId()) == gp.IgnoreMode.AdminIgnore)
                return 1; //players administratively ignored
            return 2; //sender ignoring target
        }
        else
            return 0; //neither ignoring each other
    }

    String softMessage(String targetName, String[] args)
    {
        StringBuilder prepareMessage = new StringBuilder();
        prepareMessage.append(ChatColor.GRAY);
        prepareMessage.append(ChatColor.ITALIC);
        prepareMessage.append("You whisper to ");
        prepareMessage.append(targetName);
        prepareMessage.append(":");
        for (int i = 2; i < args.length; i++)
        {
            prepareMessage.append(" ");
            prepareMessage.append(args[i]);
        }
        //I mean I guess I could've just substring the thing - would that be more efficient?
        return prepareMessage.toString();
    }

    boolean sendSoftIgnore(Player sender, Player target, String[] args)
    {
        int ignoring = isIgnored(sender, target);
        switch (ignoring)
        {
            //target ignoring sender, or administratively separated
            case 1:
                sender.sendMessage(softMessage(target.getName(), args));
                return true;
            //sender ignoring target
            case 2:
                sender.sendMessage(ChatColor.RED + "You need to " + ChatColor.GOLD + "/unignore " + target.getName() + ChatColor.RED + " to send them a whisper.");
                return true;
        }
        return false;
    }
}
