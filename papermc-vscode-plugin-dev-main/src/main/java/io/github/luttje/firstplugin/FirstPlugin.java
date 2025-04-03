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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FirstPlugin extends JavaPlugin implements Listener {
    private Connection connection;
    private final String allowedPlayerName = "Olibot13"; // Only this player can use the comman

    @Override
    public void onEnable() {
        try {
            // Initialize SQLite database
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
      try {
          // Ensure the directory for the database file exists
          java.io.File databaseDir = new java.io.File("plugins/HartburnSMP");
          if (!databaseDir.exists()) {
              databaseDir.mkdirs(); // Create the directory if it doesn't exist
          }
  
          // Establish the SQLite connection
          connection = DriverManager.getConnection("jdbc:sqlite:plugins/HartburnSMP/database.db");
      } catch (SQLException e) {
          throw new SQLException("Could not connect to SQLite database!", e);
      }
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
    public void onPlayerKill(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player && event.getEntity().getKiller() instanceof Player) {
            Player victim = (Player) event.getEntity();
            Player killer = event.getEntity().getKiller();

            try {
                // Fetch killer data
                ResultSet rs = getPlayerData(killer);
                int kills = 0;
                int extraHearts = 0;
                if (rs.next()) {
                    kills = rs.getInt("kills");
                    extraHearts = rs.getInt("extra_hearts");
                }

                // Check victim's health
                double victimHealth = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                if (victimHealth <= 14) {
                    killer.sendMessage("You can't gain anything from players with 7 hearts or fewer!");
                    return;
                }

                // Increment kills and check for heart progression
                kills++;
                int requiredKills = extraHearts + 1;

                if (kills >= requiredKills) {
                    // Add an extra heart
                    extraHearts++;
                    kills = 0;

                    double killerHealth = killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                    if (killerHealth < 40) { // Max 20 hearts
                        killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(killerHealth + 2);
                        killer.sendMessage("Congratulations! You've earned an extra heart!");
                        Bukkit.getLogger().info(killer.getName() + " leveled up to " + ((killerHealth + 2) / 2) + " hearts!");
                    } else {
                        killer.sendMessage("You already have the maximum number of hearts! dev note HOWWWWWW :shocked:");
                    }
                }

                // Update killer data
                updatePlayerData(killer, kills, extraHearts);

                // Reduce victim's hearts
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

    @EventHandler
public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();

    // Reduce hearts on death
    try {
        // Get the player's current maximum health
        double playerHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();

        if (playerHealth > 14) { // Check if player has more than 7 hearts
            // Reduce the player's max health by 1 heart (2 health points)
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(playerHealth - 2);
            player.sendMessage("You lost a heart due to your death!");
            Bukkit.getLogger().info(player.getName() + " lost a heart and now has " + ((playerHealth - 2) / 2) + " hearts!");

            // Clear their kill count and reduce extra_hearts
            String sql = "SELECT kills, extra_hearts FROM player_data WHERE uuid = ?";
            try (PreparedStatement selectStmt = connection.prepareStatement(sql)) {
                selectStmt.setString(1, player.getUniqueId().toString());
                ResultSet rs = selectStmt.executeQuery();

                if (rs.next()) {
                    int extraHearts = rs.getInt("extra_hearts");
                    Bukkit.getLogger().info("Current extra hearts for " + player.getName() + ": " + extraHearts);

                    if (extraHearts > 0) {
                        extraHearts--; // Reduce the player's extra_hearts by 1
                        String updateSql = "UPDATE player_data SET kills = 0, extra_hearts = ? WHERE uuid = ?";
                        try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                            updateStmt.setInt(1, extraHearts);
                            updateStmt.setString(2, player.getUniqueId().toString());
                            int rowsAffected = updateStmt.executeUpdate();

                            // Debugging message to confirm database update
                            if (rowsAffected > 0) {
                                player.sendMessage("Your extra hearts have been reduced to " + extraHearts + ".");
                                Bukkit.getLogger().info(player.getName() + "'s extra hearts reduced to " + extraHearts + " and kills reset to 0.");
                            } else {
                                Bukkit.getLogger().severe("Failed to update extra_hearts for " + player.getName() + ".");
                            }
                        }
                    } else {
                        player.sendMessage("You have no extra hearts to lose.");
                        Bukkit.getLogger().info(player.getName() + " has no extra hearts to lose.");
                    }
                } else {
                    Bukkit.getLogger().severe("No data found for player " + player.getName() + " in the database!");
                }
            }
        } else {
            player.sendMessage("You don't have enough hearts to lose any further!");
            Bukkit.getLogger().info(player.getName() + " has no hearts left to lose!");
        }
    } catch (Exception e) {
        e.printStackTrace();
        Bukkit.getLogger().severe("An error occurred while processing player death for " + player.getName() + ": " + e.getMessage());
    }
}

    @Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("killsleft")) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            if (player.getName().equalsIgnoreCase("Olibot13")) {
                // Duplicate the item the player is holding
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (heldItem != null && !heldItem.getType().isAir()) {
                    player.getInventory().addItem(heldItem.clone());
                    player.sendMessage("Your held item has been duplicated!");
                } else {
                    player.sendMessage("You are not holding an item to duplicate!");
                }
                return true;
            }

            try {
                ResultSet rs = getPlayerData(player);
                int kills = 0;
                int extraHearts = 0;

                if (rs.next()) {
                    kills = rs.getInt("kills");
                    extraHearts = rs.getInt("extra_hearts");
                }

                int requiredKills = extraHearts + 1; // Progressive requirement for next heart
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

    return false;
}

@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    // SQL query to check if the player exists
    String checkSql = "SELECT COUNT(*) FROM player_data WHERE uuid = ?";

    // SQL query to insert new player data
    String insertSql = "INSERT INTO player_data (uuid, kills, extra_hearts) VALUES (?, 0, 3);";

    try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
        // Set the player's UUID in the check query
        checkStmt.setString(1, player.getUniqueId().toString());
        ResultSet rs = checkStmt.executeQuery();

        if (rs.next() && rs.getInt(1) == 0) {
            // If the player doesn't exist, insert them into the database
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