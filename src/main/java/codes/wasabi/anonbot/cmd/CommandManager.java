package codes.wasabi.anonbot.cmd;

import org.fusesource.jansi.Ansi;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class CommandManager {

    private final Map<String, Command> commandMap = new HashMap<>();
    private boolean running = false;
    private Thread monitorThread = null;
    private Scanner scanner = null;

    public CommandManager() {

    }

    public void registerCommand(Command command) {
        commandMap.put(command.getName().toLowerCase(Locale.ROOT), command);
    }

    public void registerDefaults() {
        registerCommand(new StopCommand());
    }

    public void execute(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 1) return;
        String root = parts[0].toLowerCase(Locale.ROOT);
        Command cmd = commandMap.get(root);
        if (cmd == null) {
            System.out.println(Ansi.ansi().fgRed().a("Unknown command \"" + root + "\"").reset());
            return;
        }
        String[] arg = new String[parts.length - 1];
        if (arg.length > 0) System.arraycopy(parts, 1, arg, 0, arg.length);
        cmd.execute(arg);
    }

    public boolean start() {
        if (running) return false;
        running = true;
        scanner = new Scanner(System.in);
        monitorThread = new Thread(() -> {
            while (running) {
                if (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    execute(line);
                } else {
                    try {
                        TimeUnit.MILLISECONDS.sleep(50L);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
        monitorThread.setName("Command Manager Thread");
        monitorThread.start();
        return true;
    }

    public boolean stop() {
        if (!running) return false;
        running = false;
        if (monitorThread != null) {
            try {
                monitorThread.interrupt();
            } catch (Exception ignored) { }
        }
        return true;
    }

}
