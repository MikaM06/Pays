package com.massivecraft.factions.cmd;

import com.massivecraft.factions.Factions;
import com.massivecraft.factions.Rel;
import com.massivecraft.factions.RelationParticipator;
import com.massivecraft.factions.cmd.req.ReqHasntFaction;
import com.massivecraft.factions.cmd.type.TypeFactionNameStrict;
import com.massivecraft.factions.entity.Faction;
import com.massivecraft.factions.entity.FactionColl;
import com.massivecraft.factions.entity.MConf;
import com.massivecraft.factions.event.EventFactionsCreate;
import com.massivecraft.factions.event.EventFactionsMembershipChange;
import com.massivecraft.factions.event.EventFactionsMembershipChange.MembershipChangeReason;
import com.massivecraft.massivecore.MassiveException;
import com.massivecraft.massivecore.mson.Mson;
import com.massivecraft.massivecore.store.MStore;
import com.sun.deploy.config.Config;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.text.Normalizer;
import java.util.ArrayList;

public class CmdFactionsCreate extends FactionsCommand
{
	// -------------------------------------------- //
	// CONSTRUCT
	// -------------------------------------------- //
	
	public CmdFactionsCreate()
	{
		// Aliases
		this.addAliases("new");
		
		// Parameters
		this.addParameter(TypeFactionNameStrict.get(), "name");
		
		// Requirements
		this.addRequirements(ReqHasntFaction.get());
	}
	
	// -------------------------------------------- //
	// OVERRIDE
	// -------------------------------------------- //

	private boolean isFactionNameAllowed(String factionName) {
		return factionName != null && factionName.matches("[a-zA-Z0-9]*") && factionName.length() >= 3 && factionName.length() <= 16;
	}

	private void teleportPlayerToCoordinates(Player player, int x, int y, int z) {
		player.teleport(new Location(player.getWorld(), x, y, z));
	}

	@Override
	public void perform() throws MassiveException {
		String newName = this.readArg();

		if (!isFactionNameAllowed(newName)) {
			msg("<b>The name of the country entered does not exist.");
			return;
		}

		if (FactionColl.get().isNameTaken(newName)) {
			msg("<b>Cette faction existe déjà. Rejoignez-la avec la commande : /f join %s", newName);
			return;
		}

		ArrayList<String> nameValidationErrors = FactionColl.get().validateName(newName);

		if (nameValidationErrors.size() > 0) {
			System.out.println(nameValidationErrors);
			msg("<b>The country name is not valid. Please try again.");
			return;
		}

		String factionId = MStore.createId();

		EventFactionsCreate createEvent = new EventFactionsCreate(this.sender, factionId, newName);
		createEvent.run();

		if (createEvent.isCancelled()) {
			return;
		}

		Faction faction = (Faction) FactionColl.get().create(factionId);
		String factionName = newName.toUpperCase().charAt(0) + newName.substring(1);
		factionName = factionName.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
		faction.setName(factionName);

		this.msender.setRole(Rel.LEADER);
		this.msender.setFaction(faction);

		String playerName = sender.getName();

		EventFactionsMembershipChange joinEvent = new EventFactionsMembershipChange(this.sender, this.msender, faction, EventFactionsMembershipChange.MembershipChangeReason.CREATE);
		joinEvent.run();

		msg("<i>Vous avez crée le pays : %s", faction.getName((RelationParticipator) this.msender));
		msg("<i>Vous pouvez choisir une description : %s", Factions.get().getCmdFactionsDescription().getUseageTemplate());
		
		if (MConf.get().logFactionCreate) {
			Factions.get().log(String.format("%s created a new faction: %s", this.msender.getName(), newName));
		}

		try {
			String host = Config.get().getString("Base de Donnée.Host");
			String port = Config.get().getString("Base de Donnée.Port");
			String user = Config.get().getString("Base de Donnée.User");
			String password = Config.get().getString("Base de Donnée.Password");
			String database = Config.get().getString("Base de Donnée.Database");

			Connection connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, user, password);
			Statement statement = connection.createStatement();
			statement.execute("CREATE TABLE IF NOT EXISTS Pays (name VARCHAR(255), X INT, Y INT, Z INT, PRIMARY KEY (name))");

			String query = "SELECT X, Y, Z FROM Pays WHERE name = ?";
			PreparedStatement preparedStatement = connection.prepareStatement(query);
			preparedStatement.setString(1, newName);
			ResultSet resultSet = preparedStatement.executeQuery();

			Factions pluginInstance = JavaPlugin.getPlugin(Factions.class);

			if (resultSet.next()) {
				int x = resultSet.getInt("X");
				int y = resultSet.getInt("Y");
				int z = resultSet.getInt("Z");

				// If the player is too high, teleport them to the surface
				if (y > 256) {
					y = 256;
				}

				teleportPlayerToCoordinates((Player) sender, x, y, z);

				((Player) sender).performCommand("f claim o");

				// Give the player a 5-second immunity

				((Player) sender).performCommand("f sethome");
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
			msg(ChatColor.RED + "Error when creating the country. Please contact an administrator.");
		}
	}
}
