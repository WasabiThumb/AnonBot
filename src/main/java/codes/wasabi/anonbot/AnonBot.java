package codes.wasabi.anonbot;

import codes.wasabi.anonbot.cmd.CommandManager;
import codes.wasabi.anonbot.data.DMState;
import codes.wasabi.anonbot.store.Stores;
import codes.wasabi.anonbot.util.Encryption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.fusesource.jansi.Ansi;
import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import javax.security.auth.login.LoginException;
import java.util.*;
import java.awt.Color;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnonBot extends ListenerAdapter {

    private final JDA jda;
    private final CommandManager cmd;
    private final SecretKey sk;

    public AnonBot(String token, SecretKey sk, boolean quitOnFail) {
        this.sk = sk;
        JDABuilder builder = JDABuilder.createDefault(token);
        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS);
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setActivity(Activity.listening("0]help"));
        builder.addEventListeners(this);
        //
        cmd = new CommandManager();
        cmd.registerDefaults();
        JDA jda = null;
        try {
            jda = builder.build();
        } catch (LoginException | IllegalArgumentException e) {
            System.out.println(Ansi.ansi().fgRed().a("Failed to connect to discord, is the token valid?").reset());
            if (quitOnFail) System.exit(1);
        }
        this.jda = Objects.requireNonNull(jda);
    }

    public AnonBot(String token, SecretKey sk) {
        this(token, sk, false);
    }

    public AnonBot(String token, boolean quitOnFail) {
        this(token, Encryption.generateKey(), quitOnFail);
    }

    public AnonBot(String token) {
        this(token, Encryption.generateKey(), false);
    }

    public JDA getJDA() {
        return jda;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println(Ansi.ansi().fgGreen().a("Bot is ready").reset());
        cmd.start();
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        System.out.println(Ansi.ansi().fgRed().a("Bot shut down").reset());
        cmd.stop();
        System.exit(0);
    }

    private void proxyMessage(@NotNull MessageChannel destination, @NotNull Message message) {
        User author = message.getAuthor();
        long authorID = author.getIdLong();
        Random random = new Random(authorID);
        //
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(Color.getHSBColor(random.nextFloat(), 1f, 1f));
        builder.setDescription(message.getContentRaw());
        String token = "ERROR";
        try {
            token = Encryption.encrypt(authorID, sk);
        } catch (Exception e) {
            System.out.println(Ansi.ansi().fgBrightYellow().a("WARNING: ").fgYellow().a("Encryption failed for message, see details below").fgRed());
            e.printStackTrace();
            System.out.println(Ansi.ansi().reset());
        }
        builder.setFooter("Token: " + token);
        List<Message.Attachment> attachments = message.getAttachments();
        if (attachments.size() > 0) {
            StringBuilder value = new StringBuilder();
            for (int i=0; i < attachments.size(); i++) {
                if (i > 0) value.append("\n");
                value.append(attachments.get(i).getProxyUrl());
            }
            builder.addField("Attachments", value.toString(), false);
        }
        //
        List<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(builder.build());
        embeds.addAll(message.getEmbeds());
        //
        message.delete().queue();
        //
        destination.sendMessageEmbeds(embeds).queue((Message m) -> {
            Stores.OWNERS.set(m.getIdLong(), authorID);
        });
    }

    private Supplier<User> resolveUser(long id) {
        final User usr = jda.getUserById(id);
        if (usr == null) {
            return (() -> jda.retrieveUserById(id).complete());
        }
        return (() -> usr);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        Message message = event.getMessage();
        User author = message.getAuthor();
        if (author.isBot()) return;
        MessageChannel channel = message.getChannel();
        String conts = message.getContentDisplay();
        if (channel instanceof PrivateChannel) {
            if (conts.equalsIgnoreCase("0]open") || conts.equalsIgnoreCase("0]close")) {
                channel.sendMessageEmbeds((new EmbedBuilder())
                        .setColor(Color.YELLOW)
                        .setTitle("Sorry")
                        .setDescription("This feature is work in progress")
                        .build()
                ).queue();
            } else {
                long id = author.getIdLong();
                DMState state = Stores.DM.get(id);
                if (state != null) {
                    long guildID = state.guild();
                    Guild guild = jda.getGuildById(guildID);
                    if (guild == null) {
                        Stores.DM.remove(id);
                        channel.sendMessageEmbeds((new EmbedBuilder())
                                .setColor(Color.RED)
                                .setTitle("Error")
                                .setDescription("The server you are trying to send to no longer exists.")
                                .build()
                        ).queue();
                        return;
                    }
                    GuildMessageChannel gmc = guild.getChannelById(GuildMessageChannel.class, state.channel());
                    if (gmc == null) {
                        Stores.DM.remove(id);
                        channel.sendMessageEmbeds((new EmbedBuilder())
                                .setColor(Color.RED)
                                .setTitle("Error")
                                .setDescription("The channel you are trying to send to no longer exists.")
                                .build()
                        ).queue();
                        return;
                    }
                    proxyMessage(gmc, message);
                }
            }
            return;
        }
        if (conts.startsWith("0]") && conts.length() > 2) {
            String base = conts.substring(2);
            String[] parts = base.split("\\s+");
            message.delete().queue();
            String cmd = parts[0];
            String[] arg = new String[parts.length - 1];
            if (arg.length > 0) System.arraycopy(parts, 1, arg, 0, arg.length);
            boolean setting = true;
            switch (cmd.toLowerCase(Locale.ROOT)) {
                case "help":
                    channel.sendMessageEmbeds((new EmbedBuilder())
                            .setColor(Color.BLUE)
                            .setTitle("Help")
                            .setDescription("for AnonBot")
                            .addField(new MessageEmbed.Field("help", "Shows you a list of commands", false))
                            .addField(new MessageEmbed.Field("set <channel id | mention>", "Sets a channel to be controlled by AnonBot", false))
                            .addField(new MessageEmbed.Field("unset <channel id | mention>", "Unsets a channel to be controlled by AnonBot", false))
                            .addField(new MessageEmbed.Field("whois <message link | id | token>", "Uncovers who sent a message using the message link, id or token", false))
                            .addField(new MessageEmbed.Field("open", "Start talking through AnonBot with Direct Message rather than in the destination server", false))
                            .addField(new MessageEmbed.Field("close", "Stop talking through AnonBot with Direct Message", false))
                            .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                            .build()
                    ).queue();
                    break;
                case "unset":
                    setting = false;
                case "set":
                    GuildMessageChannel gc = null;
                    if (arg.length > 0) {
                        String first = arg[0];
                        try {
                            long l = Long.parseLong(first);
                            Guild guild = message.getGuild();
                            gc = guild.getChannelById(GuildMessageChannel.class, l);
                        } catch (NumberFormatException ignored) {
                        } catch (IllegalStateException e) {
                            channel.sendMessageEmbeds((new EmbedBuilder())
                                    .setColor(Color.RED)
                                    .setTitle("Error")
                                    .setDescription("You are not in a guild!")
                                    .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                    .build()
                            ).queue();
                            break;
                        }
                    }
                    if (gc == null) {
                        List<GuildChannel> list = message.getMentions().getChannels();
                        for (GuildChannel c : list) {
                            if (c instanceof GuildMessageChannel gmc) {
                                gc = gmc;
                                break;
                            }
                        }
                    }
                    if (gc == null) {
                        channel.sendMessageEmbeds((new EmbedBuilder())
                                .setColor(Color.RED)
                                .setTitle("Error")
                                .setDescription("Couldn't find that channel!")
                                .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                .build()
                        ).queue();
                        break;
                    }
                    Guild g = gc.getGuild();
                    Member member = g.getMember(author);
                    boolean privileged = false;
                    if (member != null) {
                        privileged = member.hasPermission(Permission.MANAGE_CHANNEL);
                    }
                    if (!privileged) {
                        channel.sendMessageEmbeds((new EmbedBuilder())
                                .setColor(Color.RED)
                                .setTitle("Error")
                                .setDescription("You do not have permission to do that.")
                                .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                .build()
                        ).queue();
                        break;
                    }
                    long id = g.getIdLong();
                    HashSet<Long> set = Stores.CHANNELS.get(id);
                    if (set == null) set = new HashSet<>();
                    if (setting) {
                        if (set.add(gc.getIdLong())) {
                            channel.sendMessageEmbeds((new EmbedBuilder())
                                    .setColor(Color.GREEN)
                                    .setTitle("Success")
                                    .setDescription("Added AnonBot to " + gc.getAsMention())
                                    .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                    .build()
                            ).queue();
                        } else {
                            channel.sendMessageEmbeds((new EmbedBuilder())
                                    .setColor(Color.YELLOW)
                                    .setTitle("Success")
                                    .setDescription("AnonBot was already added to " + gc.getAsMention())
                                    .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                    .build()
                            ).queue();
                        }
                    } else {
                        if (set.remove(gc.getIdLong())) {
                            channel.sendMessageEmbeds((new EmbedBuilder())
                                    .setColor(Color.GREEN)
                                    .setTitle("Success")
                                    .setDescription("Removed AnonBot from " + gc.getAsMention())
                                    .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                    .build()
                            ).queue();
                        } else {
                            channel.sendMessageEmbeds((new EmbedBuilder())
                                    .setColor(Color.YELLOW)
                                    .setTitle("Success")
                                    .setDescription("AnonBot was not previously added to " + gc.getAsMention())
                                    .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                    .build()
                            ).queue();
                        }
                    }
                    Stores.CHANNELS.set(id, set);
                    break;
                case "open":
                case "close":
                    channel.sendMessageEmbeds((new EmbedBuilder())
                            .setColor(Color.RED)
                            .setTitle("Error")
                            .setDescription("These commands must be used in DMs")
                            .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                            .build()
                    ).queue();
                    break;
                case "whois":
                    GuildMessageChannel gmc;
                    if (channel instanceof GuildMessageChannel _gmc) {
                        gmc = _gmc;
                    } else {
                        channel.sendMessageEmbeds((new EmbedBuilder())
                                .setColor(Color.RED)
                                .setTitle("Error")
                                .setDescription("This command must be used in a guild")
                                .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                .build()
                        ).queue();
                        break;
                    }
                    Member member1 = gmc.getGuild().getMember(author);
                    if (member1 == null) {
                        channel.sendMessageEmbeds((new EmbedBuilder())
                                .setColor(Color.RED)
                                .setTitle("Error")
                                .setDescription("Something very unexpected happened, please try again later")
                                .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                .build()
                        ).queue();
                        break;
                    }
                    if (!member1.hasPermission(Permission.NICKNAME_MANAGE)) {
                        channel.sendMessageEmbeds((new EmbedBuilder())
                                .setColor(Color.RED)
                                .setTitle("Error")
                                .setDescription("You don't have permission to do that")
                                .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                .build()
                        ).queue();
                        break;
                    }
                    if (arg.length < 1) {
                        channel.sendMessageEmbeds((new EmbedBuilder())
                                .setColor(Color.RED)
                                .setTitle("Error")
                                .setDescription("You need to supply a message id, link or token.")
                                .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                .build()
                        ).queue();
                        break;
                    }
                    String term = arg[0];
                    Supplier<User> getter = (() -> null);
                    Pattern tokenPattern = Pattern.compile("([A-Za-z\\d+/=]+)@([A-Za-z\\d+/=]+)");
                    Matcher matcher = tokenPattern.matcher(term);
                    if (matcher.matches()) {
                        // token
                        try {
                            long l = Encryption.decrypt(term, sk);
                            getter = resolveUser(l);
                        } catch (Exception ignored) { }
                    } else {
                        Pattern linkPattern = Pattern.compile("^https?://.*/channels/(@me|\\d+)/(\\d+)/(\\d+)/?$");
                        Matcher matcher1 = linkPattern.matcher(term);
                        if (matcher1.matches()) {
                            // link
                            String guildID = matcher1.group(1);
                            String channelID = matcher1.group(2);
                            String messageID = matcher1.group(3);
                            long messageIDLong = Long.parseLong(messageID);
                            if (Stores.OWNERS.contains(messageIDLong)) {
                                long oid = Objects.requireNonNull(Stores.OWNERS.get(messageIDLong));
                                getter = resolveUser(oid);
                            } else {
                                MessageChannel ch = null;
                                if (guildID.equals("@me")) {
                                    ch = jda.getChannelById(MessageChannel.class, channelID);
                                } else {
                                    Guild guild = jda.getGuildById(guildID);
                                    if (guild != null) {
                                        ch = guild.getChannelById(GuildMessageChannel.class, channelID);
                                    }
                                }
                                if (ch != null) {
                                    try {
                                        Message m = ch.getHistory().getMessageById(messageID);
                                        if (m != null) {
                                            for (MessageEmbed embed : m.getEmbeds()) {
                                                MessageEmbed.Footer footer = embed.getFooter();
                                                if (footer == null) continue;
                                                String cts = footer.getText();
                                                if (cts == null) continue;
                                                Matcher matcher2 = tokenPattern.matcher(cts);
                                                if (matcher2.matches()) {
                                                    try {
                                                        long l = Encryption.decrypt(cts, sk);
                                                        getter = resolveUser(l);
                                                        break;
                                                    } catch (Exception ignored) { }
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) { }
                                }
                            }
                        } else if (Pattern.compile("\\d+").matcher(term).matches()) {
                            // id
                            long l = Long.parseLong(term);
                            if (Stores.OWNERS.contains(l)) {
                                long oid = Objects.requireNonNull(Stores.OWNERS.get(l));
                                getter = resolveUser(oid);
                            }
                        }
                    }
                    final Supplier<User> finalGetter = getter;
                    Executors.newSingleThreadExecutor().execute(() -> {
                        User user = finalGetter.get();
                        if (user == null) {
                            channel.sendMessageEmbeds((new EmbedBuilder())
                                    .setColor(Color.RED)
                                    .setTitle("Error")
                                    .setDescription("Failed to find the owner. Try another method; tokens are most reliable, then links and then IDs.")
                                    .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                    .build()
                            ).queue();
                            return;
                        }
                        channel.sendMessageEmbeds((new EmbedBuilder())
                                .setColor(Color.BLUE)
                                .setTitle("Sender Found")
                                .setDescription("``@" + user.getName() + "#" + user.getDiscriminator() + "``\n" + user.getAsMention() + "\n" + user.getId())
                                .setThumbnail(user.getEffectiveAvatarUrl())
                                .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                                .build()
                        ).queue();
                    });
                    break;
                default:
                    channel.sendMessageEmbeds((new EmbedBuilder())
                            .setColor(Color.RED)
                            .setTitle("Error")
                            .setDescription("No command named \"" + cmd + "\"")
                            .setFooter("Requested by " + author.getName() + "#" + author.getDiscriminator(), author.getEffectiveAvatarUrl())
                            .build()
                    ).queue();
            }
        } else {
            if (channel instanceof GuildMessageChannel gmc) {
                Guild guild = gmc.getGuild();
                Set<Long> set = Stores.CHANNELS.get(guild.getIdLong());
                if (set != null) {
                    if (set.contains(gmc.getIdLong())) {
                        proxyMessage(gmc, message);
                    }
                }
            }
        }
    }

}
