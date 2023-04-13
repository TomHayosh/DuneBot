package controller.commands;

import controller.Initializers;
import exceptions.ChannelNotFoundException;
import model.*;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import utils.CardImages;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

public class ShowCommands {
    public static List<CommandData> getCommands() {
        List<CommandData> commandData = new ArrayList<>();
        commandData.add(
                Commands.slash("show", "Show parts of the game.").addSubcommands(
                        new SubcommandData("board", "Show the map in the turn summary"),
                        new SubcommandData("faction-info", "Print Faction Information in their Private Channel")
                                .addOptions(CommandOptions.faction),
                        new SubcommandData("front-of-shields", "Refresh the #front-of-shield channel")
                )
        );

        return commandData;
    }

    public static void runCommand(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        String name = event.getSubcommandName();

        switch (name) {
            case "board" -> showBoard(discordGame, gameState);
            case "faction-info" -> showFactionInfo(event, discordGame, gameState);
            case "front-of-shields" -> refreshFrontOfShieldInfo(event, discordGame, gameState);
        }
    }

    public static void showBoard(DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        drawGameBoard(discordGame, gameState);
    }

    public static void showFactionInfo(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        String factionName = event.getOption(CommandOptions.faction.getName()).getAsString();
        writeFactionInfo(discordGame, factionName);
    }


    private static void drawGameBoard(DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        if (gameState.getMute()) return;
        //Load png resources into a hashmap.
        HashMap<String, File> boardComponents = new HashMap<>();
        URL dir = ShowCommands.class.getClassLoader().getResource("Board Components");
        try {
            for (File file : new File(dir.toURI()).listFiles()) {
                boardComponents.put(file.getName().replace(".png", ""), file);
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }


        try {
            BufferedImage board = ImageIO.read(boardComponents.get("Board"));

            //Place destroyed Shield Wall
            if (gameState.isShieldWallDestroyed()) {
                BufferedImage brokenShieldWallImage = ImageIO.read(boardComponents.get("Shield Wall Destroyed"));
                brokenShieldWallImage = resize(brokenShieldWallImage, 256, 231);
                Point coordinates = Initializers.getDrawCoordinates("shield wall");
                board = overlay(board, brokenShieldWallImage, coordinates, 1);
            }

            //Place Tech Tokens
            for (int i = 0; i < gameState.getFactions().size(); i++) {
                Faction faction = gameState.getFactions().get(i);
                if (faction.getTechTokens().isEmpty()) continue;
                int offset = 0;
                for (TechToken token : faction.getTechTokens()) {
                    BufferedImage tokenImage = ImageIO.read(boardComponents.get(token.getName()));
                    tokenImage = resize(tokenImage, 50, 50);
                    Point coordinates = Initializers.getDrawCoordinates("tech token " + i);
                    Point coordinatesOffset = new Point(coordinates.x + offset, coordinates.y);
                    board = overlay(board, tokenImage, coordinatesOffset, 1);
                    offset += 50;
                }
            }

            //Place turn, phase, and storm markers
            BufferedImage turnMarker = ImageIO.read(boardComponents.get("Turn Marker"));
            turnMarker = resize(turnMarker, 55, 55);
            int turn = gameState.getTurn() == 0 ? 1 : gameState.getTurn();
            float angle = (turn * 36) + 74f;
            turnMarker = rotateImageByDegrees(turnMarker, angle);
            Point coordinates = Initializers.getDrawCoordinates("turn " + gameState.getTurn());
            board = overlay(board, turnMarker, coordinates, 1);
            BufferedImage phaseMarker = ImageIO.read(boardComponents.get("Phase Marker"));
            phaseMarker = resize(phaseMarker, 50, 50);
            coordinates = Initializers.getDrawCoordinates("phase " + (gameState.getPhase()));
            board = overlay(board, phaseMarker, coordinates, 1);
            BufferedImage stormMarker = ImageIO.read(boardComponents.get("storm"));
            stormMarker = resize(stormMarker, 172, 96);
            stormMarker = rotateImageByDegrees(stormMarker, -(gameState.getStorm() * 20));
            board = overlay(board, stormMarker, Initializers.getDrawCoordinates("storm " + gameState.getStorm()), 1);

            //Place sigils
            for (int i = 1; i <= gameState.getFactions().size(); i++) {
                Faction faction = gameState.getFactions().get(i - 1);
                BufferedImage sigil = ImageIO.read(boardComponents.get(faction.getName() + " Sigil"));
                coordinates = Initializers.getDrawCoordinates("sigil " + i);
                sigil = resize(sigil, 50, 50);
                board = overlay(board, sigil, coordinates, 1);

                // Check for alliances
                if (faction.hasAlly()) {
                    BufferedImage allySigil =
                            ImageIO.read(boardComponents.get(faction.getAlly() + " Sigil"));
                    coordinates = Initializers.getDrawCoordinates("ally " + i);
                    allySigil = resize(allySigil, 40, 40);
                    board = overlay(board, allySigil, coordinates, 1);
                }
            }


            //Place forces
            for (Territory territory : gameState.getTerritories().values()) {
                if (territory.getForces().size() == 0 && territory.getSpice() == 0) continue;
                if (territory.getTerritoryName().equals("Hidden Mobile Stronghold")) continue;
                int offset = 0;
                int i = 0;

                if (territory.getSpice() != 0) {
                    i = 1;
                    int spice = territory.getSpice();
                    while (spice != 0) {
                        if (spice >= 10) {
                            BufferedImage spiceImage = ImageIO.read(boardComponents.get("10 Spice"));
                            spiceImage = resize(spiceImage, 25,25);
                            Point spicePlacement = Initializers.getPoints(territory.getTerritoryName()).get(0);
                            Point spicePlacementOffset = new Point(spicePlacement.x + offset, spicePlacement.y - offset);
                            board = overlay(board, spiceImage, spicePlacementOffset, 1);
                            spice -= 10;
                        } else if (spice >= 5) {
                            BufferedImage spiceImage = ImageIO.read(boardComponents.get("5 Spice"));
                            spiceImage = resize(spiceImage, 25,25);
                            Point spicePlacement = Initializers.getPoints(territory.getTerritoryName()).get(0);
                            Point spicePlacementOffset = new Point(spicePlacement.x + offset, spicePlacement.y - offset);
                            board = overlay(board, spiceImage, spicePlacementOffset, 1);
                            spice -= 5;
                        } else if (spice >= 2) {
                            BufferedImage spiceImage = ImageIO.read(boardComponents.get("2 Spice"));
                            spiceImage = resize(spiceImage, 25,25);
                            Point spicePlacement = Initializers.getPoints(territory.getTerritoryName()).get(0);
                            Point spicePlacementOffset = new Point(spicePlacement.x + offset, spicePlacement.y - offset);
                            board = overlay(board, spiceImage, spicePlacementOffset, 1);
                            spice -= 2;
                        } else {
                            BufferedImage spiceImage = ImageIO.read(boardComponents.get("1 Spice"));
                            spiceImage = resize(spiceImage, 25,25);
                            Point spicePlacement = Initializers.getPoints(territory.getTerritoryName()).get(0);
                            Point spicePlacementOffset = new Point(spicePlacement.x + offset, spicePlacement.y - offset);
                            board = overlay(board, spiceImage, spicePlacementOffset, 1);
                            spice -= 1;
                        }
                        offset += 15;
                    }
                }
                offset = 0;
                for (Force force : territory.getForces()) {
                    if (force.getName().equals("Hidden Mobile Stronghold")) {
                        BufferedImage hms = ImageIO.read(boardComponents.get("Hidden Mobile Stronghold"));
                        hms = resize(hms, 150,100);
                        List<Force> hmsForces = gameState.getTerritories().get("Hidden Mobile Stronghold").getForces();
                        int forceOffset = 0;
                        for (Force f : hmsForces) {
                            BufferedImage forceImage = buildForceImage(boardComponents, f.getName(), f.getStrength());
                            hms = overlay(hms, forceImage, new Point(40,20 + forceOffset), 1);
                            forceOffset += 30;
                        }
                        Point forcePlacement = Initializers.getPoints(territory.getTerritoryName()).get(i);
                        Point forcePlacementOffset = new Point(forcePlacement.x - 55, forcePlacement.y + offset + 5);
                        board = overlay(board, hms, forcePlacementOffset, 1);
                        continue;
                    }
                    BufferedImage forceImage = buildForceImage(boardComponents, force.getName(), force.getStrength());
                    Point forcePlacement = Initializers.getPoints(territory.getTerritoryName()).get(i);
                    Point forcePlacementOffset = new Point(forcePlacement.x, forcePlacement.y + offset);
                    board = overlay(board, forceImage, forcePlacementOffset, 1);
                    i++;
                    if (i == Initializers.getPoints(territory.getTerritoryName()).size()) {
                        offset += 20;
                        i = 0;
                    }
                }
            }

            //Place tanks forces
            int i = 0;
            int offset = 0;
            for (Force force : gameState.getTanks()) {
                if (force.getStrength() == 0) continue;
                BufferedImage forceImage = buildForceImage(boardComponents, force.getName(), force.getStrength());

                Point tanksCoordinates = Initializers.getPoints("Forces Tanks").get(i);
                Point tanksOffset = new Point(tanksCoordinates.x, tanksCoordinates.y - offset);

                board = overlay(board, forceImage, tanksOffset, 1);
                i++;
                if (i > 1) {
                    offset += 30;
                    i = 0;
                }
            }

            //Place tanks leaders
            i = 0;
            offset = 0;
            for (Leader leader : gameState.getLeaderTanks()) {
                BufferedImage leaderImage = ImageIO.read(boardComponents.get(leader.name().replace(".", "")));
                leaderImage = resize(leaderImage, 70,70);
                Point tanksCoordinates = Initializers.getPoints("Leaders Tanks").get(i);
                Point tanksOffset = new Point(tanksCoordinates.x, tanksCoordinates.y - offset);
                board = overlay(board, leaderImage, tanksOffset, 1);
                i++;
                if (i > Initializers.getPoints("Leaders Tanks").size() - 1) {
                    offset += 70;
                    i = 0;
                }
            }

            ByteArrayOutputStream boardOutputStream = new ByteArrayOutputStream();
            ImageIO.write(board, "png", boardOutputStream);

            FileUpload boardFileUpload = FileUpload.fromData(boardOutputStream.toByteArray(), "board.png");
            discordGame.getTextChannel("turn-summary")
                    .sendFiles(boardFileUpload)
                    .queue();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static BufferedImage buildForceImage(HashMap<String, File> boardComponents, String force, int strength) throws IOException {
        BufferedImage forceImage = !force.equals("Advisor") ? ImageIO.read(boardComponents.get(force.replace("*", "") + " Troop")) : ImageIO.read(boardComponents.get("BG Advisor"));
        forceImage = resize(forceImage, 47, 29);
        if (force.contains("*")) {
            BufferedImage star = ImageIO.read(boardComponents.get("star"));
            star = resize(star, 8, 8);
            forceImage = overlay(forceImage, star, new Point(20, 7), 1);
        }
        if (strength > 9) {
            BufferedImage oneImage = ImageIO.read(boardComponents.get("1"));
            BufferedImage digitImage = ImageIO.read(boardComponents.get(String.valueOf(strength - 10)));
            oneImage = resize(oneImage, 12, 12);
            digitImage = resize(digitImage, 12,12);
            forceImage = overlay(forceImage, oneImage, new Point(28, 14), 1);
            forceImage = overlay(forceImage, digitImage, new Point(36, 14), 1);
        } else {
            BufferedImage numberImage = ImageIO.read(boardComponents.get(String.valueOf(strength)));
            numberImage = resize(numberImage, 12, 12);
            forceImage = overlay(forceImage, numberImage, new Point(30,14), 1);

        }
        return forceImage;
    }

    private static BufferedImage resize(BufferedImage img, int newW, int newH) {
        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return dimg;
    }

    private static BufferedImage overlay(BufferedImage board, BufferedImage piece, Point coordinates, float alpha) {

        int compositeRule = AlphaComposite.SRC_OVER;
        AlphaComposite ac;
        ac = AlphaComposite.getInstance(compositeRule, alpha);
        BufferedImage overlay = new BufferedImage(board.getWidth(), board.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = overlay.createGraphics();
        g.drawImage(board, 0, 0, null);
        g.setComposite(ac);
        g.drawImage(piece, coordinates.x - (piece.getWidth()/2), coordinates.y - (piece.getHeight()/2), null);
        g.setComposite(ac);
        g.dispose();

        return overlay;
    }

    private static BufferedImage rotateImageByDegrees(BufferedImage img, double angle) {
        double rads = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(rads)), cos = Math.abs(Math.cos(rads));
        int w = img.getWidth();
        int h = img.getHeight();
        int newWidth = (int) Math.floor(w * cos + h * sin);
        int newHeight = (int) Math.floor(h * cos + w * sin);

        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();
        AffineTransform at = new AffineTransform();
        at.translate((double) (newWidth - w) / 2, (double) (newHeight - h) / 2);

        int x = w / 2;
        int y = h / 2;

        at.rotate(rads, x, y);
        g2d.setTransform(at);
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();

        return rotated;
    }

    public static void writeFactionInfo(DiscordGame discordGame, String factionName) throws ChannelNotFoundException {
        writeFactionInfo(discordGame, discordGame.getGameState().getFaction(factionName));
    }

    public static void writeFactionInfo(DiscordGame discordGame, Faction faction) throws ChannelNotFoundException {

        String emoji = faction.getEmoji();
        List<TraitorCard> traitors = faction.getTraitorHand();
        StringBuilder traitorString = new StringBuilder();
        if (faction.getName().equals("BT")) traitorString.append("\n__Face Dancers:__\n");
        else traitorString.append("\n__Traitors:__\n");
        for (TraitorCard traitor : traitors) {
            if (traitor.name().equals("Cheap Hero")) {
                traitorString.append("Cheap Hero (0)\n");
                continue;
            }
            String traitorEmoji = discordGame.getGameState().getFaction(traitor.factionName()).getEmoji();
            traitorString.append(traitorEmoji).append(" ").append(traitor.name()).append("(").append(traitor.strength()).append(")");
            traitorString.append("\n");
        }
        for (TextChannel channel : discordGame.getTextChannels()) {
            if (channel.getName().equals(faction.getName().toLowerCase() + "-info")) {
                discordGame.sendMessage(channel.getName(), emoji + "**Faction Info**" + emoji + "\n__Spice:__ " +
                        faction.getSpice() +
                        traitorString);

                for (TreacheryCard treachery : faction.getTreacheryHand()) {
                    discordGame.sendMessage(channel.getName(), "<:treachery:991763073281040518> " + treachery.name() + " <:treachery:991763073281040518>");
                }
            }
        }

    }

    public static void refreshFrontOfShieldInfo(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        MessageChannel frontOfShieldChannel = discordGame.getTextChannel("front-of-shield");
        MessageHistory messageHistory = MessageHistory.getHistoryFromBeginning(frontOfShieldChannel).complete();
        List<Message> messages = messageHistory.getRetrievedHistory();

        for (Message message : messages) {
            message.delete().queue();
        }

        for (Faction faction : gameState.getFactions()) {
            StringBuilder message = new StringBuilder();
            List<FileUpload> uploads = new ArrayList<>();

            message.append(
                    MessageFormat.format(
                            "{0}{1} Front of Shield{0}\n",
                            faction.getEmoji(), faction.getName()
                    )
            );

            if (faction.getFrontOfShieldSpice() > 0) {
                message.append(faction.getFrontOfShieldSpice() + " <:spice4:991763531798167573>\n");
            }

            if (faction.getName().equalsIgnoreCase("Richese") && faction.hasResource("frontOfShieldNoField")) {
                message.append(faction.getResource("frontOfShieldNoField").getValue().toString() + " No-Field Token\n");
            }

            if (gameState.hasLeaderSkills()) {
                Optional<Leader> skilledLeader = faction.getSkilledLeader();

                if (skilledLeader.isPresent()) {
                    Leader leader = skilledLeader.get();

                    message.append(
                            MessageFormat.format(
                                    "{0} is a {1}\n",
                                    leader.name(), leader.skillCard().name()
                            )
                    );

                    Optional<FileUpload> fileUpload = CardImages
                            .getLeaderSkillImage(event.getGuild(), leader.skillCard().name());

                    fileUpload.ifPresent(uploads::add);
                }
            }

            if (gameState.hasStrongholdSkills()) {
                for (Resource strongholdCard : faction.getResources("strongholdCard")) {
                    String strongholdName = strongholdCard.getValue().toString();
                    message.append(strongholdName + " Stronghold Skill\n");

                    Optional<FileUpload> fileUpload = CardImages
                            .getStrongholdImage(event.getGuild(), strongholdName);

                    fileUpload.ifPresent(uploads::add);
                }
            }

            if (uploads.isEmpty()) {
                discordGame.sendMessage("front-of-shield", message.toString());
            } else {
                discordGame.sendMessage("front-of-shield", message.toString(), uploads);
            }
        }
    }
}
