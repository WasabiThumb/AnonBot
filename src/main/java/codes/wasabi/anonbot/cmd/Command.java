package codes.wasabi.anonbot.cmd;

import org.jetbrains.annotations.NotNull;

public interface Command {

    @NotNull String getName();

    void execute(String[] args);

}
