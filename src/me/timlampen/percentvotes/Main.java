package me.timlampen.percentvotes;

import java.security.Permissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VoteListener;

public class Main extends JavaPlugin implements VoteListener, Listener, CommandExecutor{
	Economy eco = null;
	Permission perms = null;
	private String prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix"));
	
	@Override
	public void onEnable(){
		loadItems();
		getCommand("vote").setExecutor(this);
		getCommand("rewards").setExecutor(this);
		Bukkit.getPluginManager().registerEvents(this, this);
		setupEconomy();
		setupPermissions();
	}
	
	@Override
	public void onDisable(){
		saveDefaultConfig();
	}
	
	@Override
	public void voteMade(Vote vote) {
		if(Bukkit.getPlayer(vote.getUsername())!=null){
			Player player = Bukkit.getPlayer(vote.getUsername());
			doVote(player);
		}
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
		if(sender instanceof Player){
			Player player = (Player) sender;
			if(cmd.getName().equalsIgnoreCase("vote")){
				if(getConfig().getBoolean("useGUI")){
					makeInv(player);
				}
				else{
					for(String s : getConfig().getStringList("command")){
						player.sendMessage(ChatColor.translateAlternateColorCodes('&', s));
					}
				}
			}
			else if(cmd.getName().equalsIgnoreCase("rewards")){
				for(String s : getConfig().getStringList("rewards")){
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', s));
				}
			}
		}
		return false;
	}
	
	public double getRankPercent(Player player, double percent){
		double amt = 0;
		for(String rank : getConfig().getConfigurationSection("ranks").getKeys(false)){
			if(rank.toLowerCase().equalsIgnoreCase(perms.getPrimaryGroup(player).toLowerCase())){
				double rankupamt = getConfig().getInt("ranks." + rank + ".cost");
				amt = rankupamt*(percent/100);
				player.sendMessage(percent/100 + "");
				break;
			}
		}
		
		return amt;
	}
	
	public void doVote(Player player){
		boolean doneReward = false;
		List<String> rewardnums = new ArrayList<String>(getConfig().getConfigurationSection("rewards").getKeys(false));
		Collections.shuffle(rewardnums);
		
		Random ran = new Random();
		int r = ran.nextInt(1000)+1;
		for(String rewardname : rewardnums){
			if(getConfig().getInt("rewards." + rewardname + ".chance")>=r){
				for(String cmd : getConfig().getStringList("rewards." + rewardname + ".rewards")){
					cmd = cmd.replace("%player%", player.getName());
					if(cmd.contains("%")){//its a percentage
						try{
							Integer.parseInt(cmd.replace("%", ""));
						}catch(NumberFormatException nfe){
							nfe.printStackTrace();
							return;
						}
						
						double amt = getRankPercent(player, Integer.parseInt(cmd.replace("%", "")));
						player.sendMessage(amt + "");
						eco.depositPlayer(player, amt);
						
					}
					else{
						Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
					}
				}
				doneReward = true;
				break;
			}
		}
		
		if(!doneReward){
			for(String cmd : getConfig().getStringList("Always")){
				if(cmd.contains("%")){//its a percentage
					try{
						Integer.parseInt(cmd.replace("%", ""));
					}catch(NumberFormatException nfe){
						nfe.printStackTrace();
					}
					
					double amt = getRankPercent(player, Integer.parseInt(cmd.replace("%", "")));
					eco.depositPlayer(player, amt);
					
				}
				else{
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
				}
			}
		}
	}
	
	@EventHandler
	public void onClick(InventoryClickEvent event){
		if(event.getClickedInventory()!=null && event.getInventory()!=null && event.getCurrentItem()!=null && event.getWhoClicked() instanceof Player && event.getClickedInventory().getSize()<10){
			Player player = (Player)event.getWhoClicked();
			ItemStack is = event.getCurrentItem();
			if(is.hasItemMeta() && is.getItemMeta().hasDisplayName()){
				for(String s : items.keySet()){
					if(is.getItemMeta().getDisplayName().equalsIgnoreCase(s)){
						event.setCancelled(true);
						player.sendMessage(prefix + items.get(s));
						player.closeInventory();
					}
				}
			}
		}
	}
	
	private void loadItems(){
		if(getConfig().getBoolean("useGUI")){
			for(String s : getConfig().getStringList("command")){
				String[] parts = s.split("@");
				if(parts[3]!=null){
					String name = ChatColor.translateAlternateColorCodes('&', parts[2]);
					String link = parts[3];
					items.put(name, link);
				}
				else{
					Bukkit.getConsoleSender().sendMessage("Error creating item with this line: " + s + "\n Make sure you use the tutorial in the config correctly");
					return;
				}
			}
		}
		else{
			return;
		}
	}
	private HashMap<String, String> items = new HashMap<String, String>();
	@SuppressWarnings("deprecation")
	private void makeInv(Player player){
		Inventory inv = Bukkit.createInventory(player, 9);
		for(String s : getConfig().getStringList("command")){
			String[] parts = s.split("@");
			if(parts[3]!=null){
				try{
					Integer.parseInt(parts[0]);
					Integer.parseInt(parts[1]);
				}catch(NumberFormatException nfe){
					Bukkit.getConsoleSender().sendMessage("Error creating item with this line: " + s + "\n Make sure you use the tutorial in the config correctly");
					player.sendMessage(prefix + ChatColor.RED + "Unable to parse item with this line: " + s + ", report to admin right now!");
					return;
				}
				
				int spot = Integer.parseInt(parts[0]);
				Material mat = Material.getMaterial(Integer.parseInt(parts[1]));
				String name = ChatColor.translateAlternateColorCodes('&', parts[2]);
				String link = parts[3];
				
				ItemStack is = new ItemStack(mat);
				ItemMeta im = is.getItemMeta();
				im.setDisplayName(name);
				is.setItemMeta(im);
				
				inv.setItem(spot, is);
			}
			else{
				Bukkit.getConsoleSender().sendMessage("Error creating item with this line: " + s + "\n Make sure you use the tutorial in the config correctly");
				player.sendMessage(prefix + ChatColor.RED + "Not enough parts in this line: " + s + ", report to admin right now!");
				return;
			}
		}
		
		player.openInventory(inv);
	}
	
    private boolean setupEconomy()
    {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            eco = economyProvider.getProvider();
        }

        return (eco != null);
    }
    private boolean setupPermissions()
    {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            perms = permissionProvider.getProvider();
        }
        return (perms != null);
    }
}
