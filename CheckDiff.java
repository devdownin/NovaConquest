import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CheckDiff {
    public static void main(String[] args) throws IOException {
        String screen = new String(Files.readAllBytes(Paths.get("./app/src/main/kotlin/com/novaempire/app/ui/screens/TacticalMapScreen.kt")));
        String hex = new String(Files.readAllBytes(Paths.get("./core/hex/src/main/kotlin/com/novaempire/core/hex/HexCoord.kt")));

        System.out.println("TacticalMapScreen hexRound exists? " + screen.contains("fun hexRound"));
        System.out.println("HexCoord round exists? " + hex.contains("fun round"));
    }
}
