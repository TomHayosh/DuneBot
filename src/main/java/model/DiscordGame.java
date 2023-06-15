package model;

import com.google.gson.Gson;
import enums.GameOption;
import exceptions.ChannelNotFoundException;
import io.gsonfire.GsonFireBuilder;
import model.factions.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class DiscordGame {
    private final Member member;
    private final Category gameCategory;
    private List<TextChannel> textChannelList;
    private Game gameState;
    private boolean disableModCheck = false;

    public DiscordGame(@NotNull SlashCommandInteractionEvent event) throws ChannelNotFoundException {
        this.gameCategory = event.getChannel().asTextChannel().getParentCategory();
        this.member = event.getMember();
        this.gameState = this.getGameState();
    }

    public DiscordGame(@NotNull SlashCommandInteractionEvent event, @NotNull Category category) {
        this.gameCategory = category;
        this.member = event.getMember();
    }

    public DiscordGame(@NotNull CommandAutoCompleteInteractionEvent event) {
        this.gameCategory = Objects.requireNonNull(event.getChannel()).asTextChannel().getParentCategory();
        this.member = event.getMember();
    }

    public DiscordGame(Category category, boolean disableModCheck) {
        this.gameCategory = category;
        this.member = null;
        this.disableModCheck = disableModCheck;
    }

    public Category getGameCategory() {
        return this.gameCategory;
    }

    public List<TextChannel> getTextChannels() {
        if (this.textChannelList == null) {
            this.textChannelList = this.gameCategory.getTextChannels();
        }

        return this.textChannelList;
    }

    public TextChannel getTextChannel(String name) throws ChannelNotFoundException {
        for (TextChannel channel : this.getTextChannels()) {
            if (channel.getName().equals(name)) {
                return channel;
            }
        }
        throw new ChannelNotFoundException("The channel was not found");
    }

    public TextChannel getBotDataChannel() throws ChannelNotFoundException {
        return this.getTextChannel("bot-data");
    }

    public boolean isModRole(String modRoleName) {
        return disableModCheck || this.member
                .getRoles()
                .stream().map(Role::getName)
                .toList()
                .contains(modRoleName);
    }

    public void setGameState(Game gameState) {
        this.gameState = gameState;
    }

    public Game getGameState() throws ChannelNotFoundException {
        if (this.gameState == null) {
            MessageHistory h = this.getBotDataChannel()
                    .getHistory();

            h.retrievePast(1).complete();

            List<Message> ml = h.getRetrievedHistory();
            Message.Attachment encoded = ml.get(0).getAttachments().get(0);
            CompletableFuture<InputStream> future = encoded.getProxy().download();

            try {
                String gameStateString = new String(future.get().readAllBytes(), StandardCharsets.UTF_8);
                Gson gson = createGsonDeserializer();
                Game returnGame = gson.fromJson(gameStateString, Game.class);
                future.get().close();
                migrateGameState(returnGame);
                if (!this.isModRole(returnGame.getModRole())) {
                    throw new IllegalArgumentException("ERROR: command issuer does not have specified moderator role");
            }
            return returnGame;
        } catch (IOException | InterruptedException | ExecutionException e) {
            System.out.println("Didn't work...");
            return new Game();}
        }
        return this.gameState;
    }

    /**
     * Creates a Gson object that can deserialize the GameState object.
     *
     * @return Gson object that can deserialize the GameState object.
     */
    public static Gson createGsonDeserializer() {
        GsonFireBuilder builder = new GsonFireBuilder()
                .registerTypeSelector(Faction.class, new FactionTypeSelector());
        return builder.createGson();
    }

    public static void migrateGameState(Game game) {
        if (game.getGameOptions() == null) {
            game.setGameOptions(new HashSet<>());
        }

        if (game.hasTechTokens()) {
            game.addGameOption(GameOption.TECH_TOKENS);
        }

        if (game.hasLeaderSkills()) {
            game.addGameOption(GameOption.LEADER_SKILLS);
        }

        if (game.hasStrongholdSkills()) {
            game.addGameOption(GameOption.STRONGHOLD_SKILLS);
        }
    }

    public void pushGameState() {
        Gson gson = new Gson();
        FileUpload fileUpload = FileUpload.fromData(
                gson.toJson(this.gameState).getBytes(StandardCharsets.UTF_8), "gamestate.json"
        );

        try {
            this.getBotDataChannel().sendFiles(fileUpload).complete();
        } catch (ChannelNotFoundException e) {
            System.out.println("Channel not found. State was not saved.");
        }
    }

    public void sendMessage(String name, String message) throws ChannelNotFoundException {
        TextChannel channel = getTextChannel(name);
        if (this.gameState.getMute()) return;
        channel.sendMessage(message).queue();
    }

    public void sendMessage(String name, MessageCreateData message) throws ChannelNotFoundException {
        TextChannel channel = getTextChannel(name);
        if (this.gameState.getMute()) return;
        channel.sendMessage(message).queue();
    }

    public void sendMessage(String name, String message, FileUpload fileUpload) throws ChannelNotFoundException {
        TextChannel channel = getTextChannel(name);
        if (this.gameState.getMute()) return;
        channel.sendMessage(message).addFiles(fileUpload).queue();
    }

    public void sendMessage(String name, String message, List<FileUpload> fileUploads) throws ChannelNotFoundException {
        TextChannel channel = getTextChannel(name);
        if (this.gameState.getMute()) return;
        channel.sendMessage(message).addFiles(fileUploads).queue();
    }

    /**
     * Creates a thread in the parent channel with the given name and adds the given users to it.
     * @param parentChannelName The name of the parent channel
     * @param threadName The name of the thread to create
     * @param userIds The ids of the users to add to the thread.  All non-numeeric characters will be removed.
     * @throws ChannelNotFoundException
     */
    public void createThread(String parentChannelName, String threadName, List<String> userIds) throws ChannelNotFoundException {
        TextChannel channel = getTextChannel(parentChannelName);

        channel.createThreadChannel(threadName, true)
                .setInvitable(false)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
                .queue(
                        threadChannel -> addUsersToThread(threadChannel, userIds)
                );
    }

    /**
     * Adds the given users to the given thread.
     * @param threadChannel The thread to add the users to
     * @param userIds The ids of the users to add to the thread.  All non-numeeric characters will be removed.
     */
    private void addUsersToThread(ThreadChannel threadChannel, List<String> userIds) {
        JDA jda = threadChannel.getJDA();

        userIds.forEach(
                userId -> jda.retrieveUserById(userId.replaceAll("\\D", "")).queue(
                        user -> threadChannel.addThreadMember(user).queue()
                )
        );
    }
}
