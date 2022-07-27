package codes.wasabi.anonbot.cmd;

import codes.wasabi.anonbot.Main;
import org.fusesource.jansi.Ansi;
import org.jetbrains.annotations.NotNull;

public class StopCommand implements Command {

    @Override
    public @NotNull String getName() {
        return "stop";
    }

    @Override
    public void execute(String[] args) {
        System.out.println(Ansi.ansi().fgYellow().a("Shutting down...").reset());
        Main.INSTANCE.getJDA().shutdown();

    }

}
