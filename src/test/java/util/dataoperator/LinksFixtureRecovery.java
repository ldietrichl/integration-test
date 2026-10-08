package util.dataoperator;
import java.nio.file.Path;
/** Run this main class with the project's test classpath and selected environment after an interrupted JVM. */
public final class LinksFixtureRecovery {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Expected absolute fixture-manifest.json path");
        LinksFixtureSession.recover(Path.of(args[0]));
        System.out.println("Fixture recovery completed; inspect fixture-cleanup.json");
    }
}
