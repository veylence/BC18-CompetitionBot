import bc.*;

/*
 * TODO: Update the ==0, ==1 checks when methods are updated to return booleans
 */
public class Player {
    // This player's game controller
    protected GameController gc;
    // Holds the player AI for this planet
    private PlanetPlayer player;

    /**
     * Initializes the player for a new game.
     */
    public Player() {
        // Connect ot the manager and start the game
        this.gc = new GameController();

        Planet planet = this.gc.planet();
        if (planet == Planet.Earth) {
            this.player = new EarthPlayer(this.gc, planet);
        } else {
            this.player = new MarsPlayer(this.gc, planet);
        }

        this.gc.queueResearch(UnitType.Ranger);
        this.gc.queueResearch(UnitType.Ranger);
        this.gc.queueResearch(UnitType.Ranger);
        this.gc.queueResearch(UnitType.Rocket);
    }

    public static void main(String[] args) {
        Player player = new Player();
        player.start();
    }

    /**
     * Start playing the game until the game ends.
     */
    public void start() {
        // MapLocation is a data structure you'll use a lot.
        // MapLocation loc = new MapLocation(Planet.Earth, 10, 20);
        // System.out.println("loc: " + loc + ", one step to the Northwest: " + loc.add(Direction.Northwest));
        // System.out.println("loc x: " + loc.getX());

        // One slightly weird thing: some methods are currently static methods on a static class called bc.
        // This will eventually be fixed :/
        // System.out.println("Opposite of " + Direction.North + ": " + bc.bcDirectionOpposite(Direction.North));

        // Keep playing turns as long as possible
        while (true) {
            // Catch all exceptions, since crashing will disable all play for the
            // rest of the game.
            try {
                this.player.processPreTurn();
                this.player.processTurn();
                this.player.processPostTurn();
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.gc.nextTurn();

        }
    }
}