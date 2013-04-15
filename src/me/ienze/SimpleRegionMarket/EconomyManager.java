package me.ienze.SimpleRegionMarket;

import java.util.logging.Level;
import java.util.regex.Pattern;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.nijikokun.register.payment.Method;
import com.nijikokun.register.payment.Methods;

import me.ienze.SimpleRegionMarket.handlers.LangHandler;

public class EconomyManager {
	public static final String BANK_PREFIX = "$";
	public static final String BANK_PREFIX_QUOTED = Pattern.quote(BANK_PREFIX);
	public static final String BANK_PREFIX_REPLACE = "^" + BANK_PREFIX_QUOTED;

	private final SimpleRegionMarket plugin;

	private int enableEconomy;
	private Economy economy;

	public EconomyManager(SimpleRegionMarket plugin) {
		this.plugin = plugin;
	}

	public void setupEconomy() {
		final Server server = plugin.getServer();
		enableEconomy = SimpleRegionMarket.configurationHandler.getBoolean("Enable_Economy") ? 1 : 0;
		if (enableEconomy > 0) {
			if (server.getPluginManager().getPlugin("Register") == null && server.getPluginManager().getPlugin("Vault") == null) {
				LangHandler.directOut(Level.WARNING, "MAIN.WARN.NO_ECO_API");
				enableEconomy = 0;
			} else if (server.getPluginManager().getPlugin("Register") != null && server.getPluginManager().getPlugin("Vault") == null) {
				enableEconomy = 1;
			} else {
				enableEconomy = 2;
				if (!setupVaultEconomy()) {
					LangHandler.directOut(Level.WARNING, "MAIN.WARN.VAULT_NO_ECO");
					enableEconomy = 0;
				}
			}
		}
	}

	private Boolean setupVaultEconomy() {
		final RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager()
				.getRegistration(net.milkbowl.vault.economy.Economy.class);
		if (economyProvider != null) {
			economy = economyProvider.getProvider();
		}
		
		return economy != null;
	}

	public boolean hasBankSupport() {
		return enableEconomy == 2 && economy.hasBankSupport() && !economy.getName().startsWith("iConomy");
	}

	public boolean isBank(String name) {
		return name.startsWith(BANK_PREFIX);
	}

	public String stripBankPrefix(String name) {
		return name.replaceFirst(BANK_PREFIX_REPLACE, "");
	}

	public Method getEconomicManager() {
		if (Methods.hasMethod()) {
			return Methods.getMethod();
		} else {
			LangHandler.directOut(Level.WARNING, "MAIN.WARN.REGISTER_NO_ECO");
			enableEconomy = 0;
			return null;
		}
	}

	public boolean isEconomy() {
		return enableEconomy > 1 || (enableEconomy == 1 && getEconomicManager() != null);
	}

	public boolean econGiveMoney(String account, double money) {
		if (money == 0) {
			LangHandler.directOut(Level.FINEST, "[EconomyManager] Money is zero");
			return true;
		}
		try {
			if (enableEconomy == 1) {
				if (getEconomicManager() != null) {
					if (money > 0) {
						LangHandler.directOut(Level.FINEST, "[EconomyManager - Register] Adding " + String.valueOf(money) + " to Account " + account);
						getEconomicManager().getAccount(account).add(money);
					} else {
						LangHandler.directOut(Level.FINEST, "[EconomyManager - Register] Subtracting " + String.valueOf(money) + " from Account "
								+ account);
						getEconomicManager().getAccount(account).subtract(-money);
					}
				}
			} else if (enableEconomy == 2) {
				if (isBank(account)) {
					if (hasBankSupport()) {
						account = stripBankPrefix(account);
						if (money > 0) {
							LangHandler.directOut(Level.FINEST, "[EconomyManager - Register] Adding " + String.valueOf(money) + " to Bank Account " + account);
							return economy.bankDeposit(account, money).transactionSuccess();
						} else {
							LangHandler.directOut(Level.FINEST, "[EconomyManager] Money is zero");
							return economy.bankWithdraw(account, -money).transactionSuccess();
						}
					} else {
						return false;
					}
				} else {
					if (money > 0) {
						LangHandler.directOut(Level.FINEST, "[EconomyManager - Register] Adding " + String.valueOf(money) + " to Account " + account);
						return economy.depositPlayer(account, money).transactionSuccess();
					} else {
						LangHandler.directOut(Level.FINEST, "[EconomyManager] Money is zero");
						return economy.withdrawPlayer(account, -money).transactionSuccess();
					}
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean econHasEnough(String account, double money) {
		if (money == 0) {
			return true;
		}
		if (enableEconomy == 1) {
			if (getEconomicManager() != null) {
				return getEconomicManager().getAccount(account).hasEnough(money);
			}
		} else if (enableEconomy == 2) {
			if (isBank(account)) {
				if (hasBankSupport()) {
					account = stripBankPrefix(account);
					return economy.bankHas(account, money).transactionSuccess();
				} else {
					return false;
				}
			} else {
				return economy.has(account, money);
			}
		}
		return false;
	}

	public String econFormat(double price) {
		String ret = null;
		if (enableEconomy == 1) {
			if (getEconomicManager() != null) {
				ret = getEconomicManager().format(price);
			}
		} else if (enableEconomy == 2) {
			ret = economy.format(price);
		}
		if(ret == null) {
			ret = String.valueOf(price);
		}
		return ret;
	}

	public boolean moneyTransaction(String from, String to, double money) {
		try {
			if (to == null) {
				if (econHasEnough(from, money)) {
					econGiveMoney(from, -money);
					return true;
				} else {
					LangHandler.ErrorOut(Bukkit.getPlayer(from), "PLAYER.ERROR.NO_MONEY", null);
					return false;
				}
			} else if (from == null) {
				econGiveMoney(to, money);
				return true;
			} else {
				if (econHasEnough(from, money)) {
					econGiveMoney(from, -money);
					econGiveMoney(to, money);
					return true;
				} else {
					LangHandler.ErrorOut(Bukkit.getPlayer(from), "PLAYER.ERROR.NO_MONEY", null);
					return false;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		if(Bukkit.getPlayer(from) != null) {
			LangHandler.ErrorOut(Bukkit.getPlayer(from), "PLAYER.ERROR.ECO_PROBLEM", null);
		}
		return false;
	}
}
