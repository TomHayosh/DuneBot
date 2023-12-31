package model;

import constants.Emojis;
import controller.commands.CommandManager;
import controller.commands.ShowCommands;
import exceptions.ChannelNotFoundException;
import model.factions.Faction;

import java.io.IOException;

public class TechToken {
    private final String name;
    private int spice;

    public TechToken(String name) {
        this.name = name;
        this.spice = 0;
    }

    public static void addSpice (Game gameState, DiscordGame discordGame, String techToken) throws ChannelNotFoundException {
        for (Faction faction : gameState.getFactions()) {
            if (faction.getTechTokens().isEmpty()) continue;
            for (TechToken tt : faction.getTechTokens()) {
                if (tt.getName().equals(techToken) && tt.spice == 0) {
                    tt.spice = faction.getTechTokens().size();
                    discordGame.sendMessage("turn-summary", tt.spice + " " + Emojis.SPICE + " is placed on " + Emojis.getTechTokenEmoji(techToken));
                }
            }
        }
    }

    public static void collectSpice(Game gameState, DiscordGame discordGame, String techToken) throws ChannelNotFoundException, IOException {
        for (Faction faction : gameState.getFactions()) {
            if (faction.getTechTokens().isEmpty()) continue;
            for (TechToken tt : faction.getTechTokens()) {
                if (tt.getName().equals(techToken) && tt.spice > 0) {
                    faction.addSpice(tt.spice);
                    discordGame.sendMessage("turn-summary", faction.getEmoji() +  " collects " + tt.spice + " " + Emojis.SPICE + " for " + Emojis.getTechTokenEmoji(techToken));
                    CommandManager.spiceMessage(discordGame, tt.spice, faction.getName(), "for " + Emojis.getTechTokenEmoji(techToken), true);
                    ShowCommands.writeFactionInfo(discordGame, faction);
                    tt.spice = 0;
                    break;
                }
            }
        }
    }

    public String getName() {
        return name;
    }
}
