package controller.commands;

import exceptions.ChannelNotFoundException;
import exceptions.InvalidGameStateException;
import io.github.cdimascio.dotenv.Dotenv;
import model.*;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import templates.ChannelPermissions;

import java.text.MessageFormat;
import java.util.*;
import java.util.List;

public class CommandManager extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        Member member = event.getMember();

        List<Role> roles = member == null ? new ArrayList<>() : member.getRoles();

        if (roles.stream().noneMatch(role ->
                role.getName().equals("Game Master") || role.getName().equals("Dungeon Master"))
        ) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String name = event.getName();
        event.deferReply(true).queue();

        try {
            if (name.equals("newgame")) newGame(event);
            else if (name.equals("clean")) clean(event);
            else {
                DiscordGame discordGame = new DiscordGame(event);
                Game gameState = discordGame.getGameState();

                switch (name) {
                    case "show" -> ShowCommands.runCommand(event, discordGame, gameState);
                    case "setup" -> SetupCommands.runCommand(event, discordGame, gameState);
                    case "run" -> RunCommands.runCommand(event, discordGame, gameState);
                    case "richese" -> RicheseCommands.runCommand(event, discordGame, gameState);
                    case "bt" -> BTCommands.runCommand(event, discordGame, gameState);
                    case "resourceaddorsubtract" -> resourceAddOrSubtract(event, discordGame, gameState);
                    case "removeresource" -> removeResource(event, discordGame, gameState);
                    case "draw" -> drawCard(event, discordGame, gameState);
                    case "discard" -> discard(event, discordGame, gameState);
                    case "transfercard" -> transferCard(event, discordGame, gameState);
                    case "putback" -> putBack(event, discordGame, gameState);
                    case "placeforces" -> placeForces(event, discordGame, gameState);
                    case "moveforces" -> moveForces(event, discordGame, gameState);
                    case "removeforces" -> removeForces(event, discordGame, gameState);
                    case "display" -> displayGameState(event, discordGame, gameState);
                    case "reviveforces" -> revival(event, discordGame, gameState);
                    case "awardbid" -> awardBid(event, discordGame, gameState);
                    case "killleader" -> killLeader(event, discordGame, gameState);
                    case "reviveleader" -> reviveLeader(event, discordGame, gameState);
                    case "setstorm" -> setStorm(event, discordGame, gameState);
                    case "bgflip" -> bgFlip(event, discordGame, gameState);
                    case "bribe" -> bribe(event, discordGame, gameState);
                    case "mute" -> mute(discordGame, gameState);
                    case "placehms" -> placeHMS(event, discordGame, gameState);
                    case "movehms" -> moveHMS(event, discordGame, gameState);
                    case "assigntechtoken" -> assignTechToken(event, discordGame, gameState);
                    case "draw-spice-blow" -> drawSpiceBlow(event, discordGame, gameState);
                    case "create-alliance" -> createAlliance(event, discordGame, gameState);
                    case "remove-alliance" -> removeAlliance(event, discordGame, gameState);
                }
            }
            event.getHook().editOriginal("Command Done").queue();
        } catch (ChannelNotFoundException e) {
            e.printStackTrace();
            event.getHook().editOriginal("Channel not found!").queue();
        } catch (Exception e) {
            event.getHook().editOriginal("An error occurred!").queue();
            e.printStackTrace();
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        DiscordGame discordGame = new DiscordGame(event);

        try {
            Game gameState = discordGame.getGameState();
            event.replyChoices(CommandOptions.getCommandChoices(event, gameState)).queue();
        } catch (ChannelNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        //add new slash command definitions to commandData list
        List<CommandData> commandData = new ArrayList<>();
        commandData.add(Commands.slash("clean", "FOR TEST ONLY: DO NOT RUN").addOptions(CommandOptions.password));
        commandData.add(Commands.slash("newgame", "Creates a new Dune game instance.").addOptions(CommandOptions.gameName, CommandOptions.gameRole, CommandOptions.modRole));
        commandData.add(Commands.slash("resourceaddorsubtract", "Performs basic addition and subtraction of numerical resources for factions").addOptions(CommandOptions.faction, CommandOptions.resourceName, CommandOptions.amount, CommandOptions.message));
        commandData.add(Commands.slash("removeresource", "Removes a resource category entirely (Like if you want to remove a Tech Token from a player)").addOptions(CommandOptions.faction, CommandOptions.resourceName));
        commandData.add(Commands.slash("draw", "Draw a card from the top of a deck.").addOptions(CommandOptions.deck, CommandOptions.faction));
        commandData.add(Commands.slash("discard", "Move a card from a faction's hand to the discard pile").addOptions(CommandOptions.faction, CommandOptions.card));
        commandData.add(Commands.slash("transfercard", "Move a card from one faction's hand to another").addOptions(CommandOptions.faction, CommandOptions.card, CommandOptions.recipient));
        commandData.add(Commands.slash("putback", "Used for the Ixian ability to put a treachery card on the top or bottom of the deck.").addOptions(CommandOptions.putBackCard, CommandOptions.bottom));
        commandData.add(Commands.slash("placeforces", "Place forces from reserves onto the surface").addOptions(CommandOptions.faction, CommandOptions.amount, CommandOptions.isShipment, CommandOptions.starred, CommandOptions.territory));
        commandData.add(Commands.slash("moveforces", "Move forces from one territory to another").addOptions(CommandOptions.faction, CommandOptions.fromTerritory, CommandOptions.toTerritory, CommandOptions.amount, CommandOptions.starredAmount));
        commandData.add(Commands.slash("removeforces", "Remove forces from the board.").addOptions(CommandOptions.faction, CommandOptions.amount, CommandOptions.toTanks, CommandOptions.starred, CommandOptions.fromTerritory));
        commandData.add(Commands.slash("awardbid", "Designate that a card has been won by a faction during bidding phase.").addOptions(CommandOptions.faction, CommandOptions.spent, CommandOptions.paidToFaction));
        commandData.add(Commands.slash("reviveforces", "Revive forces for a faction.").addOptions(CommandOptions.faction, CommandOptions.revived, CommandOptions.starred));
        commandData.add(Commands.slash("display", "Displays some element of the game to the mod.").addOptions(CommandOptions.data));
        commandData.add(Commands.slash("setstorm", "Sets the storm to an initial sector.").addOptions(CommandOptions.sector));
        commandData.add(Commands.slash("killleader", "Send a leader to the tanks.").addOptions(CommandOptions.faction, CommandOptions.leader));
        commandData.add(Commands.slash("reviveleader", "Revive a leader from the tanks.").addOptions(CommandOptions.faction, CommandOptions.reviveLeader));
        commandData.add(Commands.slash("bgflip", "Flip BG forces to advisor or fighter.").addOptions(CommandOptions.bgTerritories));
        commandData.add(Commands.slash("mute", "Toggle mute for all bot messages."));
        commandData.add(Commands.slash("bribe", "Record a bribe transaction").addOptions(CommandOptions.faction, CommandOptions.recipient, CommandOptions.amount));
        commandData.add(Commands.slash("placehms", "Starting position for Hidden Mobile Stronghold").addOptions(CommandOptions.territory));
        commandData.add(Commands.slash("movehms", "Move Hidden Mobile Stronghold to another territory").addOptions(CommandOptions.territory));
        commandData.add(Commands.slash("assigntechtoken", "Assign a Tech Token to a Faction (taking it away from previous owner)").addOptions(CommandOptions.faction, CommandOptions.token));
        commandData.add(Commands.slash("draw-spice-blow", "Draw the spice blow").addOptions(CommandOptions.spiceBlowDeck));
        commandData.add(Commands.slash("create-alliance", "Create an alliance between two factions")
                .addOptions(CommandOptions.faction, CommandOptions.otherFaction));
        commandData.add(Commands.slash("remove-alliance", "Remove alliance (only on faction of the alliance needs to be selected)")
                .addOptions(CommandOptions.faction));

        commandData.addAll(ShowCommands.getCommands());
        commandData.addAll(SetupCommands.getCommands());
        commandData.addAll(RunCommands.getCommands());
        commandData.addAll(RicheseCommands.getCommands());
        commandData.addAll(BTCommands.getCommands());

        event.getGuild().updateCommands().addCommands(commandData).queue();
    }

    public void newGame(SlashCommandInteractionEvent event) throws ChannelNotFoundException {
        Role gameRole = event.getOption("gamerole").getAsRole();
        Role modRole = event.getOption("modrole").getAsRole();
        Role observerRole = event.getGuild().getRolesByName("Observer", true).get(0);
        Role pollBot = event.getGuild().getRolesByName("EasyPoll", true).get(0);
        String name = event.getOption("name").getAsString();

        // Create category and set base permissions to deny everything for everyone except the mod role.
        // The channel permissions assume that this is set this way.
        event.getGuild()
                .createCategory(name)
                .addPermissionOverride(modRole, ChannelPermissions.all, null)
                .addPermissionOverride(event.getGuild().getPublicRole(), null, ChannelPermissions.all)
                .addPermissionOverride(gameRole, null, ChannelPermissions.all)
                .addPermissionOverride(observerRole, null, ChannelPermissions.all)
                .complete();

        Category category = event.getGuild().getCategoriesByName(name, true).get(0);

        category.createTextChannel("chat")
                .addPermissionOverride(
                        observerRole,
                        ChannelPermissions.readWriteAllow,
                        ChannelPermissions.readWriteDeny
                )
                .addPermissionOverride(
                        gameRole,
                        ChannelPermissions.readWriteAllow,
                        ChannelPermissions.readWriteDeny
                )
                .complete();

        // Not including Observer in pre-game-voting because there's no way to stop someone from adding to an
        // existing emoji reaction.
        category.createTextChannel("pre-game-voting")
                .addPermissionOverride(
                        gameRole,
                        ChannelPermissions.readAndReactAllow,
                        ChannelPermissions.readAndReactDeny
                )
                .addPermissionOverride(
                        pollBot,
                        ChannelPermissions.pollBotAllow,
                        ChannelPermissions.pollBotDeny
                )
                .complete();

        String[] readAndReactChannels  = {"turn-summary", "rules"};

        for (String channel : readAndReactChannels) {
            category.createTextChannel(channel)
                    .addPermissionOverride(
                            observerRole,
                            ChannelPermissions.readAndReactAllow,
                            ChannelPermissions.readAndReactDeny
                    )
                    .addPermissionOverride(
                            gameRole,
                            ChannelPermissions.readAndReactAllow,
                            ChannelPermissions.readAndReactDeny
                    )
                    .complete();
        }

        String[] readWriteChannels = {"game-actions", "bribes", "bidding-phase"};
        for (String channel : readWriteChannels) {
            category.createTextChannel(channel)
                    .addPermissionOverride(
                            observerRole,
                            ChannelPermissions.readAndReactAllow,
                            ChannelPermissions.readAndReactDeny
                    )
                    .addPermissionOverride(
                            gameRole,
                            ChannelPermissions.readWriteAllow,
                            ChannelPermissions.readWriteDeny
                    )
                    .complete();
        }

        String[] modChannels  = {"bot-data", "mod-info"};
        for (String channel : modChannels) {
            category.createTextChannel(channel).complete();
        }

        DiscordGame discordGame = new DiscordGame(event, category);
        discordGame.getTextChannel("rules").sendMessage("""
            <:DuneRulebook01:991763013814198292>  Dune rulebook: https://www.gf9games.com/dunegame/wp-content/uploads/Dune-Rulebook.pdf
            <:weirding:991763071775297681>  Dune FAQ Nov 20: https://www.gf9games.com/dune/wp-content/uploads/2020/11/Dune-FAQ-Nov-2020.pdf
            <:ix:991763319406997514> <:bt:991763325576810546>  Ixians & Tleilaxu Rules: https://www.gf9games.com/dunegame/wp-content/uploads/2020/09/IxianAndTleilaxuRulebook.pdf
            <:choam:991763324624703538> <:rich:991763318467465337> CHOAM & Richese Rules: https://www.gf9games.com/dune/wp-content/uploads/2021/11/CHOAM-Rulebook-low-res.pdf""").queue();

        Game game = new Game();
        game.setGameRole(gameRole.getName());
        game.setModRole(modRole.getName());
        game.setMute(false);
        discordGame.setGameState(game);
        discordGame.pushGameState();
    }

    public void resourceAddOrSubtract(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException, InvalidGameStateException {
        String factionName = event.getOption("factionname").getAsString();
        String resourceName = event.getOption("resource").getAsString();
        int amount = event.getOption("amount").getAsInt();
        String message = event.getOption("message").getAsString();
        Faction faction = gameState.getFaction(factionName);

        if (resourceName.equalsIgnoreCase("spice")) {
            if (amount < 0) {
                faction.subtractSpice(-amount);
            } else {
                faction.addSpice(amount);
            }
            String gainsOrLoses = amount >= 0 ? " gains " : " loses ";
            discordGame.sendMessage(
                    "turn-summary",
                    MessageFormat.format(
                            "{0} {1} {2} <:spice4:991763531798167573> {3}",
                            faction.getEmoji(), gainsOrLoses, Math.abs(amount), message
                    )
            );
            spiceMessage(discordGame, Math.abs(amount), faction.getName(), message, amount >= 0);

            ShowCommands.writeFactionInfo(discordGame, gameState.getFaction(factionName));
            discordGame.pushGameState();
            return;
        }

        Resource resource = gameState.getFaction(factionName).getResource(resourceName);

        if (resource instanceof IntegerResource) {
            ((IntegerResource) resource).addValue(amount);
        } else {
            throw new InvalidGameStateException("Resource is not numeric");
        }

        ShowCommands.writeFactionInfo(discordGame, gameState.getFaction(factionName));
        discordGame.pushGameState();
    }

    public void removeResource(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        gameState.getFaction(event.getOption("factionname").getAsString()).removeResource(event.getOption("resource").getAsString());
        discordGame.pushGameState();
    }

    public void drawSpiceBlow(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        String spiceBlowDeck = event.getOption(CommandOptions.spiceBlowDeck.getName()).getAsString();

        LinkedList<SpiceCard> deck = gameState.getSpiceDeck();
        LinkedList<SpiceCard> discard = spiceBlowDeck.equalsIgnoreCase("A") ? gameState.getSpiceDiscardA() : gameState.getSpiceDiscardB();

        StringBuilder message = new StringBuilder();

        SpiceCard drawn;

        message.append("**Spice Deck " + spiceBlowDeck + "**\n");

        do {
            if (deck.isEmpty()) {
                deck.addAll(gameState.getSpiceDiscardA());
                deck.addAll(gameState.getSpiceDiscardB());
                Collections.shuffle(deck);
                gameState.getSpiceDiscardA().clear();
                gameState.getSpiceDiscardB().clear();
            }

            drawn = deck.pop();
            discard.add(drawn);
            message.append(drawn.name() + "\n");
        } while (drawn.name().equalsIgnoreCase("Shai-Hulud"));

        if (gameState.getStorm() == drawn.sector())
            message.append(" (blown away by the storm!)");
        else
            gameState.getTerritories().get(drawn.name()).addSpice(drawn.spice());

        discordGame.pushGameState();

        discordGame.sendMessage("turn-summary", message.toString());
        ShowCommands.showBoard(discordGame, gameState);

    }

    public void drawCard(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Faction faction = gameState.getFaction(event.getOption("factionname").getAsString());
        faction.addTreacheryCard(gameState.getTreacheryDeck().pop());
        discordGame.pushGameState();
    }

    public void discard(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Faction faction = gameState.getFaction(event.getOption("factionname").getAsString());
        List<TreacheryCard> hand = faction.getTreacheryHand();
        int i = 0;
        for (; i < hand.size(); i++) {
            String card = hand.get(i).name();
            if (card.toLowerCase().contains(event.getOption("card").getAsString().toLowerCase())) {
                gameState.getTreacheryDiscard().add(hand.get(i));
                break;
            }
        }
        hand.remove(i);
        ShowCommands.writeFactionInfo(discordGame, faction);
        discordGame.pushGameState();
    }

    public void transferCard(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Faction giver = gameState.getFaction(event.getOption("sender").getAsString());
        Faction receiver = gameState.getFaction(event.getOption("recipient").getAsString());
        List<TreacheryCard> giverHand = giver.getTreacheryHand();
        List<TreacheryCard> receiverHand = receiver.getTreacheryHand();

        if ((receiver.getHandLimit() == receiverHand.size())) {
            event.getUser().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("The recipient's hand is full!").queue());
            return;
        }
        int i = 0;

        boolean cardFound = false;
        for (; i < giverHand.size(); i++) {
            String card = giverHand.get(i).name();
            if (card.toLowerCase().contains(event.getOption("card").getAsString())) {
                cardFound = true;
                receiverHand.add(giverHand.get(i));
                break;
            }
        }
        if (!cardFound) {
            event.getUser().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("Could not find that card!").queue());
            return;
        }
        giverHand.remove(i);
        ShowCommands.writeFactionInfo(discordGame, giver);
        ShowCommands.writeFactionInfo(discordGame, receiver);
        discordGame.pushGameState();
    }

    public void putBack(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        LinkedList<TreacheryCard> market = gameState.getMarket();
        int i = 0;
        boolean found = false;
        for (; i < market.size(); i++) {
            if (market.get(i).name().contains(event.getOption("putbackcard").getAsString())) {
                if (!event.getOption("bottom").getAsBoolean()) gameState.getTreacheryDeck().addLast(market.get(i));
                else gameState.getTreacheryDeck().addFirst(market.get(i));
                found = true;
                break;
            }
        }
        if (!found) {
            event.getUser().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("Card not found, are you sure it's there?").queue());
            return;
        }
        market.remove(i);
        Collections.shuffle(market);
        if (gameState.hasFaction("Atreides")) {
            discordGame.sendMessage("atreides-chat","The first card up for bid is <:treachery:991763073281040518> " + gameState.getMarket().peek().name() + " <:treachery:991763073281040518>");
        }
        discordGame.pushGameState();
    }

    public void awardBid(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Faction winner = gameState.getFaction(event.getOption("factionname").getAsString());
        String paidToFactionName = event.getOption("paid-to-faction", "Bank", OptionMapping::getAsString);
        List<TreacheryCard> winnerHand = winner.getTreacheryHand();
        int spent = event.getOption("spent").getAsInt();

        String currentCard = MessageFormat.format(
                "R{0}:C{1}",
                gameState.getTurn(),
                gameState.getBidCardNumber()
        );

        discordGame.sendMessage("turn-summary",
                MessageFormat.format(
                        "{0} wins {1} for {2} <:spice4:991763531798167573>",
                        winner.getEmoji(),
                        currentCard,
                        spent
                )
        );

        // Winner pays for the card
        winner.subtractSpice(spent);
        spiceMessage(discordGame, spent, winner.getName(), currentCard, false);

        if (gameState.hasFaction(paidToFactionName)) {
            Faction paidToFaction = gameState.getFaction(paidToFactionName);
            spiceMessage(discordGame, spent, paidToFaction.getName(), currentCard, true);
            gameState.getFaction(paidToFaction.getName()).addSpice(spent);
            discordGame.sendMessage("turn-summary",
                    MessageFormat.format(
                            "{0} is paid {1} <:spice4:991763531798167573> for {2}",
                            paidToFaction.getEmoji(),
                            spent,
                            currentCard
                    )
            );
            ShowCommands.writeFactionInfo(discordGame, paidToFaction);
        }

        winnerHand.add(gameState.getBidCard());
        gameState.setBidCard(null);

        // Harkonnen draw an additional card
        if (winner.getName().equals("Harkonnen") && winnerHand.size() < winner.getHandLimit()) {
            if (gameState.getTreacheryDeck().isEmpty()) {
                List<TreacheryCard> treacheryDiscard = gameState.getTreacheryDiscard();
                discordGame.sendMessage("turn-summary", "The Treachery Deck has been replenished from the Discard Pile");
                gameState.getTreacheryDeck().addAll(treacheryDiscard);
                treacheryDiscard.clear();
            }

            gameState.drawCard("treachery deck", "Harkonnen");
            discordGame.sendMessage("turn-summary", winner.getEmoji() + " draws another card from the <:treachery:991763073281040518> deck.");
        }

        ShowCommands.writeFactionInfo(discordGame, winner);

        discordGame.pushGameState();
    }

    public static void spiceMessage(DiscordGame discordGame, int amount, String faction, String message, boolean plus) throws ChannelNotFoundException {
        String plusSign = plus ? "+" : "-";
        for (TextChannel channel : discordGame.getTextChannels()) {
            if (channel.getName().equals(faction.toLowerCase() + "-info")) {
                discordGame.sendMessage(channel.getName(), plusSign + amount + "<:spice4:991763531798167573> " + message);
            }
        }
    }

    public void killLeader(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Faction faction = gameState.getFaction(event.getOption("factionname").getAsString());
        gameState.getLeaderTanks().add(faction.removeLeader(event.getOption("leadertokill").getAsString()));
        discordGame.pushGameState();
    }

    public void reviveLeader(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Faction faction = gameState.getFaction(event.getOption("factionname").getAsString());
        faction.getLeaders().add(gameState.removeLeaderFromTanks(event.getOption("leadertorevive").getAsString()));
        discordGame.pushGameState();
    }

    public void revival(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        String star = event.getOption("starred").getAsBoolean() ? "*" : "";
        Faction faction = gameState.getFaction(event.getOption("factionname").getAsString());
        if (star.equals("")) faction.getReserves().addStrength(event.getOption("revived").getAsInt());
        else faction.getSpecialReserves().addStrength(event.getOption("revived").getAsInt());
        faction.subtractSpice(2 * event.getOption("revived").getAsInt());
        spiceMessage(discordGame, 2 * event.getOption("revived").getAsInt(), faction.getName(), "Revivals", false);
        if (gameState.hasFaction("BT")) {
            gameState.getFaction("BT").addSpice(2 * event.getOption("revived").getAsInt());
            spiceMessage(discordGame, 2 * event.getOption("revived").getAsInt(), "bt", faction.getEmoji() + " revivals", true);
            ShowCommands.writeFactionInfo(discordGame, gameState.getFaction("BT"));
        }
        ShowCommands.writeFactionInfo(discordGame, faction);
        discordGame.pushGameState();
    }

    public void placeForces(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        boolean star = event.getOption("starred").getAsBoolean();
        Territory territory = gameState.getTerritories().get(event.getOption("territory").getAsString());
        Faction faction = gameState.getFaction(event.getOption("factionname").getAsString());
        int amount = event.getOption("amount").getAsInt();

        Force reserves = star ? faction.getSpecialReserves() : faction.getReserves();
        if (reserves.getStrength() < amount) {
            discordGame.sendMessage("mod-info", "This faction does not have enough forces in reserves!");
            return;
        }
        reserves.setStrength(reserves.getStrength() - amount);

        Force force = territory.getForce(reserves.getName());
        if (force.getStrength() == 0) territory.getForces().add(force);
        force.addStrength(amount);

        if (event.getOption("isshipment").getAsBoolean()) {
            int costPerForce = territory.isStronghold() ? 1 : 2;
            int cost = costPerForce * amount;

            // Guild has half price shipping
            if (faction.getName().equalsIgnoreCase("Guild"))
                cost = Math.ceilDiv(cost, 2);

            faction.subtractSpice(cost);
            spiceMessage(discordGame, cost, faction.getName(), "shipment to " + territory.getTerritoryName(), false);
            if (gameState.hasFaction("Guild") && !(faction.getName().equals("Guild") || faction.getName().equals("Fremen"))) {
                gameState.getFaction("Guild").addSpice(cost);
                spiceMessage(discordGame, cost, "guild", faction.getEmoji() + " shipment", true);
                ShowCommands.writeFactionInfo(discordGame, gameState.getFaction("Guild"));
            }
            ShowCommands.writeFactionInfo(discordGame, faction);
        }

        discordGame.pushGameState();
    }

    public void moveForces(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Faction faction = gameState.getFaction(event.getOption("factionname").getAsString());
        Territory from = gameState.getTerritories().get(event.getOption("from").getAsString());
        Territory to = gameState.getTerritories().get(event.getOption("to").getAsString());
        int amount = event.getOption("amount").getAsInt();
        int starredAmount = event.getOption("starredamount").getAsInt();

        from.setForceStrength(faction.getName(), from.getForce(faction.getName()).getStrength() - amount);
        from.setForceStrength(faction.getName() + "*", from.getForce(faction.getName() + "*").getStrength() - starredAmount);

        to.setForceStrength(faction.getName(), to.getForce(faction.getName()).getStrength() + amount);
        to.setForceStrength(faction.getName() + "*", to.getForce(faction.getName() + "*").getStrength() + starredAmount);

        discordGame.pushGameState();
    }

    public void removeForces(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Territory territory = gameState.getTerritories().get(event.getOption("from").getAsString());
        Faction faction = gameState.getFaction(event.getOption("factionname").getAsString());
        int amount = event.getOption("amount").getAsInt();
        String starred = event.getOption("starred").getAsBoolean() ? "*" : "";
        String forceName = faction.getName() + starred;
        Force force = territory.getForce(forceName);
        if (force.getStrength() > amount) {
            force.setStrength(force.getStrength() - amount);
        } else if (force.getStrength() < amount) {
            discordGame.sendMessage("mod-info","You are trying to remove more forces than this faction has in this territory! Please check your info and try again.");
            return;
        } else {
            territory.getForces().remove(force);
        }

        if (event.getOption("totanks").getAsBoolean()) {
            gameState.getForceFromTanks(forceName).addStrength(amount);
        } else {
            if (starred.equals("*")) faction.getSpecialReserves().addStrength(amount);
            else faction.getReserves().addStrength(amount);
        }
        discordGame.pushGameState();
    }

    public void setStorm(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        gameState.setStorm(event.getOption("sector").getAsInt());
        discordGame.sendMessage("turn-summary","The storm has been initialized to " + event.getOption("sector").getAsInt() + " sectors");
        if (gameState.hasTechTokens()) {
            List<TechToken> techTokens = new LinkedList<>();
            if (gameState.hasFaction("BT")) {
                gameState.getFaction("BT").getTechTokens().add(new TechToken("Axlotl Tanks"));
            } else techTokens.add(new TechToken("Axlotl Tanks"));
            if (gameState.hasFaction("Ix")) {
                gameState.getFaction("Ix").getTechTokens().add(new TechToken("Heighliners"));
            } else techTokens.add(new TechToken("Heighliners"));
            if (gameState.hasFaction("Fremen")) {
                gameState.getFaction("Fremen").getTechTokens().add(new TechToken("Spice Production"));
            } else techTokens.add(new TechToken("Spice Production"));
            if (!techTokens.isEmpty()) {
                Collections.shuffle(techTokens);
                for (int i = 0; i < techTokens.size(); i++) {
                    Faction faction = gameState.getFactions().get((Math.ceilDiv(gameState.getStorm(), 3) - 1 + i) % 6);
                    faction.getTechTokens().add(techTokens.get(i));
                }
            }
        }
        discordGame.pushGameState();
        ShowCommands.showBoard(discordGame, gameState);
    }

    public void bgFlip(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Territory territory = gameState.getTerritories().get(event.getOption("bgterritories").getAsString());
        int strength = 0;
        String found = "";
        for (Force force : territory.getForces()) {
            if (force.getName().equals("BG") || force.getName().equals("Advisor")) {
               strength = force.getStrength();
               found = force.getName();
            }
        }
        territory.getForces().removeIf(force -> force.getName().equals("BG") || force.getName().equals("Advisor"));
        if (found.equals("Advisor")) territory.getForces().add(new Force("BG", strength));
        else if (found.equals("BG")) territory.getForces().add(new Force("Advisor", strength));
        else {
            discordGame.sendMessage("mod-info","No Bene Gesserit were found in that territory.");
            return;
        }
        discordGame.pushGameState();
        ShowCommands.showBoard(discordGame, gameState);
    }

    public void placeHMS(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Territory territory = gameState.getTerritories().get(event.getOption("territory").getAsString());
        territory.getForces().add(new Force("Hidden Mobile Stronghold", 1));
        discordGame.pushGameState();
        ShowCommands.showBoard(discordGame, gameState);
    }

    public void moveHMS(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        for (Territory territory : gameState.getTerritories().values()) {
            territory.getForces().removeIf(force -> force.getName().equals("Hidden Mobile Stronghold"));
        }
        placeHMS(event, discordGame, gameState);
    }

    public void assignTechToken(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        for (Faction faction : gameState.getFactions()) {
            faction.getTechTokens().removeIf(techToken -> techToken.getName().equals(event.getOption("token").getAsString()));
        }
        gameState.getFaction(event.getOption("factionname").getAsString()).getTechTokens().add(new TechToken(event.getOption("token").getAsString()));
        discordGame.sendMessage("turn-summary", event.getOption("token").getAsString() + " has been transferred to " + gameState.getFaction(event.getOption("factionname").getAsString()).getEmoji());
        ShowCommands.showBoard(discordGame, gameState);
        discordGame.pushGameState();
    }

    public void bribe(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        Faction faction = gameState.getFaction(event.getOption("factionname").getAsString());
        Faction recipient = gameState.getFaction(event.getOption("recipient").getAsString());
        int amount = event.getOption("amount").getAsInt();

        if (faction.getSpice() < amount) {
            discordGame.getTextChannel("mod-info").sendMessage("Faction does not have enough spice to pay the bribe!").queue();
            return;
        }
        faction.subtractSpice(amount);
        spiceMessage(discordGame, amount, faction.getName(), "bribe to " + recipient.getEmoji(), false);

        discordGame.sendMessage(
                "turn-summary",
                MessageFormat.format(
                        "{0} places {1} <:spice4:991763531798167573> in front of {2} shield.",
                        faction.getEmoji(), amount, recipient.getEmoji()
                )
        );

        recipient.addFrontOfShieldSpice(amount);
        ShowCommands.writeFactionInfo(discordGame, faction);
        discordGame.pushGameState();
    }

    public void mute(DiscordGame discordGame, Game gameState) {
        gameState.setMute(!gameState.getMute());

        discordGame.pushGameState();
    }

    public void displayGameState(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) throws ChannelNotFoundException {
        TextChannel channel = discordGame.getTextChannel("mod-info");
        switch (event.getOption("data").getAsString()) {
            case "territories" -> {
               Map<String, Territory> territories = gameState.getTerritories();
               for (Territory territory: territories.values()) {
                   if (territory.getSpice() == 0 && !territory.isStronghold() && territory.getForces().isEmpty()) continue;
                   discordGame.sendMessage(channel.getName(), "**" + territory.getTerritoryName() + "** \n" +
                           "Spice: " + territory.getSpice() + "\nForces: " + territory.getForces().toString());
               }
            }
            case "dnd" -> {
                discordGame.sendMessage("mod-info", gameState.getTreacheryDeck().toString());
                discordGame.sendMessage("mod-info", gameState.getTreacheryDiscard().toString());
                discordGame.sendMessage("mod-info", gameState.getSpiceDeck().toString());
                discordGame.sendMessage("mod-info", gameState.getSpiceDiscardA().toString());
                discordGame.sendMessage("mod-info", gameState.getSpiceDiscardB().toString());
                discordGame.sendMessage("mod-info", gameState.getLeaderSkillDeck().toString());
                discordGame.sendMessage("mod-info", gameState.getTraitorDeck().toString());
                discordGame.sendMessage("mod-info", gameState.getMarket().toString());
            }
            case "factions" -> {
                for (Faction faction: gameState.getFactions()) {
                    StringBuilder message = new StringBuilder();
                    message.append("**").append(faction.getName()).append(":**\nPlayer: ").append(faction.getUserName()).append("\n");
                    message.append("spice: ").append(faction.getSpice()).append("\nTreachery Cards: ").append(faction.getTreacheryHand())
                            .append("\nTraitors:").append(faction.getTraitorHand()).append("\nLeaders: ").append(faction.getLeaders()).append("\n");
                    for (Resource resource : faction.getResources()) {
                        message.append(resource.getName()).append(": ").append(resource.getValue()).append("\n");
                    }
                    discordGame.sendMessage(channel.getName(), message.toString());
                }
            }
        }
        ShowCommands.showBoard(discordGame, gameState);
    }

    public void clean(SlashCommandInteractionEvent event) {
        if (!event.getOption("password").getAsString().equals(Dotenv.configure().load().get("PASSWORD"))) {
            event.getChannel().sendMessage("""
                    You have attempted the forbidden command.

                    ...Or you're Voiceofonecrying and you fat-fingered the password""").queue();
            return;
        }
        List<Category> categories = event.getGuild().getCategories();
        for (Category category : categories) {
            //if (!category.getName().startsWith("test")) continue;
            category.delete().complete();
        }
        List<TextChannel> channels = event.getGuild().getTextChannels();
        for (TextChannel channel : channels) {
            if (//!channel.getName().startsWith("test") ||
            channel.getName().equals("general")) continue;
            channel.delete().complete();
        }
    }

    public void createAlliance(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) {
        Faction faction = gameState.getFaction(event.getOption(CommandOptions.faction.getName()).getAsString());
        Faction otherFaction = gameState.getFaction(event.getOption(CommandOptions.otherFaction.getName()).getAsString());

        removeAlliance(gameState, faction);
        removeAlliance(gameState, otherFaction);

        faction.setAlly(otherFaction.getName());
        otherFaction.setAlly(faction.getName());

        discordGame.pushGameState();
    }

    public void removeAlliance(SlashCommandInteractionEvent event, DiscordGame discordGame, Game gameState) {
        Faction faction = gameState.getFaction(event.getOption(CommandOptions.faction.getName()).getAsString());
        removeAlliance(gameState, faction);
    }

    private void removeAlliance(Game gameState, Faction faction) {
        if (faction.hasAlly()) {
            gameState.getFaction(faction.getAlly()).removeAlly();
        }
        faction.removeAlly();
    }
}
