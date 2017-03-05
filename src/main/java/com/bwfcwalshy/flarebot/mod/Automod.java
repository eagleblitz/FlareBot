package com.bwfcwalshy.flarebot.mod;

import com.bwfcwalshy.flarebot.FlareBot;
import com.bwfcwalshy.flarebot.MessageUtils;
import com.bwfcwalshy.flarebot.util.SQLController;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public enum Automod {
    INVITES(MessageUtils::hasInvite, SeverityLevel.MEDIUM);

    private static final Map<String, Boolean> MODS = new ConcurrentHashMap<>();
    private static final AtomicInteger THREADS = new AtomicInteger(0);
    private static final ExecutorService AUTOMOD_POOL = Executors.newCachedThreadPool(r -> new Thread(r, "Automod Thread " + THREADS.incrementAndGet()));

    static {
        try {
            SQLController.runSqlTask(conn -> {
                conn.createStatement().execute("CREATE TABLE IF NOT EXISTS automods (\n" +
                        "guild VARCHAR(20) PRIMARY KEY,\n" +
                        "enabled ENUM('y', 'n')\n" +
                        ")");
                ResultSet set = conn.createStatement().executeQuery("SELECT * FROM automods");
                while (set.next()) {
                    MODS.put(set.getString("guild"), set.getString("enabled").equals("y"));
                }
            });
        } catch (SQLException e) {
            FlareBot.LOGGER.error("Could not load automod status configs!", e);
        }
    }

    private final Predicate<Message> test;
    private final SeverityLevel severityLevel;

    Automod(Predicate<Message> test, SeverityLevel severityLevel) {
        this.test = test;
        this.severityLevel = severityLevel;
    }

    public SeverityLevel getSeverityLevel() {
        return severityLevel;
    }

    public boolean tryOn(Message message) {
        if (test.test(message)) {
            SeverityProvider.getSeverityFor(message.getGuild(), this)
                    .accept(message.getGuild().getMember(message.getAuthor()), message.getTextChannel(), this);
            return true;
        }
        return false;
    }

    public static boolean isEnabled(Guild guild) {
        return MODS.getOrDefault(guild.getId(), false);
    }

    public static void asyncProcess(GuildMessageReceivedEvent event) {
        AUTOMOD_POOL.submit(() -> {
            if (!FlareBot.getInstance().getPermissions(event.getChannel()).hasPermission(event.getMember(), "flarebot.noautomod"))
                for (Automod a : values())
                    a.tryOn(event.getMessage());
        });
    }
}
