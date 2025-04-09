package io.github.luttje.firstplugin;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.command.Command;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;

public class FirstPlugin extends JavaPlugin implements Listener {
    private Connection connection;

    @Override
    public void onEnable() {
        try {
            openConnection();
            createTable();
            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("Hartburn SMP has been enabled with SQLite!");
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Could not initialize database!");
        }
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        getLogger().info("Hartburn SMP has been disabled!");
    }

    private void openConnection() throws SQLException {
        java.io.File databaseDir = new java.io.File("plugins/HartburnSMP");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }

        connection = DriverManager.getConnection("jdbc:sqlite:plugins/HartburnSMP/database.db");
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS player_data (" +
                     "uuid TEXT PRIMARY KEY, " +
                     "kills INTEGER DEFAULT 0, " +
                     "extra_hearts INTEGER DEFAULT 0)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }

    @EventHandler
    public void onPlayerKill(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player && event.getEntity().getKiller() instanceof Player) {
            Player victim = (Player) event.getEntity();
            Player killer = event.getEntity().getKiller();

            try {
                ResultSet rs = getPlayerData(killer);
                int kills = 0;
                int extraHearts = 0;
                if (rs.next()) {
                    kills = rs.getInt("kills");
                    extraHearts = rs.getInt("extra_hearts");
                }

                double victimHealth = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                if (victimHealth <= 14) {
                    killer.sendMessage("You can't gain anything from players with 7 hearts or fewer!");
                    return;
                }

                kills++;
                int requiredKills = extraHearts + 1;

                if (kills >= requiredKills) {
                    extraHearts++;
                    kills = 0;

                    double killerHealth = killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                    if (killerHealth < 40) {
                        killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(killerHealth + 2);
                        killer.sendMessage("Congratulations! You've earned an extra heart!");
                        Bukkit.getLogger().info(killer.getName() + " leveled up to " + ((killerHealth + 2) / 2) + " hearts!");
                    } else {
                        killer.sendMessage("You already have the maximum number of hearts!");
                    }
                }

                updatePlayerData(killer, kills, extraHearts);

                if (victimHealth > 14) {
                    victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(victimHealth - 2);
                    victim.sendMessage("You lost a heart to " + killer.getName() + "!");
                    Bukkit.getLogger().info(victim.getName() + " lost a heart and now has " + ((victimHealth - 2) / 2) + " hearts!");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                killer.sendMessage("An error occurred while processing your kill!");
            }
        }
    }

    @Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("killsleft")) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            try {
                ResultSet rs = getPlayerData(player);
                int kills = 0;
                int extraHearts = 0;

                if (rs.next()) {
                    kills = rs.getInt("kills");
                    extraHearts = rs.getInt("extra_hearts");
                }

                int requiredKills = extraHearts + 1;
                int killsLeft = requiredKills - kills;

                if (killsLeft > 0) {
                    player.sendMessage("You need " + killsLeft + " more kill(s) to gain your next heart!");
                } else {
                    player.sendMessage("You are ready to gain your next heart! Earn a kill to level up.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage("An error occurred while checking your kills!");
            }
        } else {
            sender.sendMessage("This command can only be run by a player!");
        }

        return true;
    }
if (command.getName().equalsIgnoreCase("addhart")) {
    if (sender instanceof Player) {
        Player player = (Player) sender;

        if (args.length != 1) {
            player.sendMessage("Usage: /addhart <playerName>");
            return false;
        }

        String targetPlayerName = args[0];

        if (!player.hasPermission("yourplugin.addhart")) {
            player.sendMessage("You don't have permission to use this command.");
            return false;
        }

        Player targetPlayer = Bukkit.getServer().getPlayer(targetPlayerName);

        if (targetPlayer == null) {
            player.sendMessage("The player " + targetPlayerName + " is not online.");
            return false;
        }

        try {
            ResultSet rs = getPlayerData(targetPlayer);
            int extraHearts = 0;
            if (rs.next()) {
                extraHearts = rs.getInt("extra_hearts");
            }

            extraHearts++;

            int kills = 0; 
            updatePlayerData(player, kills, extraHearts);
            double maxHealth = targetPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            targetPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth + 2.0);  // Remove 1 heart (2 health points)


            player.sendMessage("Successfully added a heart to " + targetPlayerName + ".");
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage("An error occurred while adding a heart.");
        }
    } else {
        sender.sendMessage("This command can only be run by a player!");
    }

    return true;
}

if (command.getName().equalsIgnoreCase("removehart")) {
    if (sender instanceof Player) {
        Player player = (Player) sender;

        if (args.length != 1) {
            player.sendMessage("Usage: /removehart <playerName>");
            return false;
        }

        String targetPlayerName = args[0];

        if (!player.hasPermission("yourplugin.removehart")) {
            player.sendMessage("You don't have permission to use this command.");
            return false;
        }

        Player targetPlayer = Bukkit.getServer().getPlayer(targetPlayerName);

        if (targetPlayer == null) {
            player.sendMessage("The player " + targetPlayerName + " is not online.");
            return false;
        }

        try {
            ResultSet rs = getPlayerData(targetPlayer);
            int extraHearts = 0;
            if (rs.next()) {
                extraHearts = rs.getInt("extra_hearts");
            }

  
            if (extraHearts <= 0) {
                player.sendMessage("The player does not have any hearts to remove.");
                return false;
            }

    
            extraHearts--;


            int kills = 0; 
            updatePlayerData(player, kills, extraHearts);
            double maxHealth = targetPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            targetPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth - 2.0);  // Remove 1 heart (2 health points)

            player.sendMessage("Successfully removed a heart from " + targetPlayerName + ".");
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage("An error occurred while removing a heart.");
        }
    } else {
        sender.sendMessage("This command can only be run by a player!");
    }

    return true;
}



    return false;
}



    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        try {
            double playerHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();

            if (playerHealth > 14) {
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(playerHealth - 2);
                player.sendMessage("You lost a heart due to your death!");
                Bukkit.getLogger().info(player.getName() + " lost a heart and now has " + ((playerHealth - 2) / 2) + " hearts!");

                String sql = "SELECT kills, extra_hearts FROM player_data WHERE uuid = ?";
                try (PreparedStatement selectStmt = connection.prepareStatement(sql)) {
                    selectStmt.setString(1, player.getUniqueId().toString());
                    ResultSet rs = selectStmt.executeQuery();

                    if (rs.next()) {
                        int extraHearts = rs.getInt("extra_hearts");

                        if (extraHearts > 0) {
                            extraHearts--;
                            String updateSql = "UPDATE player_data SET kills = 0, extra_hearts = ? WHERE uuid = ?";
                            try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                                updateStmt.setInt(1, extraHearts);
                                updateStmt.setString(2, player.getUniqueId().toString());
                                updateStmt.executeUpdate();
                                player.sendMessage("Your extra hearts have been reduced to " + extraHearts + ".");
                            }
                        }
                    }
                }
            } else {
                player.sendMessage("You don't have enough hearts to lose any further!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("An error occurred while processing player death for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void updatePlayerData(Player player, int kills, int extraHearts) throws SQLException {
        String sql = "INSERT INTO player_data (uuid, kills, extra_hearts) VALUES (?, ?, ?) " +
                     "ON CONFLICT(uuid) DO UPDATE SET kills = ?, extra_hearts = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setInt(2, kills);
            stmt.setInt(3, extraHearts);
            stmt.setInt(4, kills);
            stmt.setInt(5, extraHearts);
            stmt.executeUpdate();
        }
    }

    private ResultSet getPlayerData(Player player) throws SQLException {
        String sql = "SELECT kills, extra_hearts FROM player_data WHERE uuid = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, player.getUniqueId().toString());
        return stmt.executeQuery();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        String checkSql = "SELECT COUNT(*) FROM player_data WHERE uuid = ?";
        String insertSql = "INSERT INTO player_data (uuid, kills, extra_hearts) VALUES (?, 0, 3);";

        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                    insertStmt.setString(1, player.getUniqueId().toString());
                    insertStmt.executeUpdate();
                    Bukkit.getLogger().info("Added new player to the database: " + player.getName());
                }
            } else {
                Bukkit.getLogger().info("Player already exists in the database: " + player.getName());
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error processing player join for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
