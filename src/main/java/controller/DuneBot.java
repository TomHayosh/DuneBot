package controller;

import controller.commands.CommandManager;
import controller.listeners.EventListener;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import okhttp3.OkHttpClient;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DuneBot {

    public static void main(String[] args) {
        Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE);
        try {
            String token = Dotenv.configure().load().get("TOKEN");

            JDABuilder.createDefault(token)
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.playing("Dune"))
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .build()
                    .addEventListener(new EventListener(), new CommandManager());
        } catch (DotenvException e) {
            System.err.println("Dotenv file or Token not found.");
            throw new RuntimeException(e);
        } catch (InvalidTokenException e) {
            System.err.println("Invalid Token");
            throw new RuntimeException(e);
        }
    }
}
