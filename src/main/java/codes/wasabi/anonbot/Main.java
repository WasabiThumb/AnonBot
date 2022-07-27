package codes.wasabi.anonbot;

import codes.wasabi.anonbot.store.Stores;
import codes.wasabi.anonbot.util.Encryption;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.internal.Kernel32;
import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import java.util.*;

public class Main {

    public static AnonBot INSTANCE;

    public static void main(String[] args) {
        boolean isWindows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows");
        if (isWindows) {
            final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;
            long console = Kernel32.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
            int[] mode = new int[1];
            if (Kernel32.GetConsoleMode(console, mode) != 0) {
                Kernel32.SetConsoleMode(console, mode[0] | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
            }
        }
        System.out.print(Ansi.ansi().eraseScreen().cursor(1, 1));
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("= ").fgBrightMagenta().a("AnonBot").fg(Ansi.Color.YELLOW).a(" =").reset());
        StartupArgs arg = getArgs(args);
        Stores.init();
        Stores.loadAll();
        INSTANCE = new AnonBot(arg.token, arg.secret, true);
        Thread thread = new Thread(() -> {
            System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Saving stores...").reset());
            Stores.saveAll();
            System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("Goodbye!").reset());
        });
        thread.setName("Main Shutdown Hook");
        Runtime.getRuntime().addShutdownHook(thread);
    }

    private record StartupArgs(String token, SecretKey secret) { }

    private static @NotNull StartupArgs getArgs(String[] args) {
        String token = null;
        String secret = null;
        Iterator<String> it = Arrays.stream(args).iterator();
        while (it.hasNext()) {
            String term = it.next();
            if (term.equalsIgnoreCase("--token")) {
                if (it.hasNext()) token = it.next();
            } else if (term.equalsIgnoreCase("--secret")) {
                if (it.hasNext()) secret = it.next();
            }
        }
        if (token == null) {
            System.out.println(Ansi.ansi().fgRed().a("No token supplied! Use --token <TOKEN> to supply a bot token!").reset());
            System.exit(1);
        }
        SecretKey sk;
        if (secret == null) {
            sk = Encryption.generateKey();
        } else {
            sk = Encryption.generateKey(secret);
        }
        return new StartupArgs(token, sk);
    }

}
