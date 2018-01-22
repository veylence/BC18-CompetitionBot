import bc.*;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

public class EarthPlayer extends PlanetPlayer {
    // "Radius" of square
    private static final int DEPOSIT_SCAN_RADIUS = 1;
    // In squared units
    private static final int POD_SCAN_RADIUS = 16;

    private int[][] depositMap;
    // Key: UnitID, Value: Pod of UnitIDs
    private Map<Integer, Set<Integer>> podsMap;
    // All worker pods
    private List<Set<Integer>> pods;
    private Map<Set<Integer>, Order> podOrders;
    private Map<Set<Integer>, MapLocation> podMiningTargets;
    private Map<Set<Integer>, Integer> podBuildingTargets;

    public EarthPlayer(GameController gc, Planet planet) {
        super(gc, planet);

        // initializeNavigator();
        findKarboniteDeposits();
        makePods();
        assignInitialPods();
    }

    // private void initializeNavigator() {
    //     // boolean[][] passable = new boolean[this.mapHeight][this.mapWidth];
    //     // for (int y = 0; y < this.mapHeight; y++) {
    //     //     for (int x = 0; x < this.mapWidth; x++) {
    //     //         passable[y][x] = this.karboniteMap[y][x] != IMPASSABLE;
    //     //     }
    //     // }
    //     this.navigator = new Navigator(this.gc, this.passableMap);
    //
    //     // this.navigator.precomputeNavMaps(this.gc.planet());
    // }

    private void findKarboniteDeposits() {
        this.depositMap = new int[this.mapHeight][this.mapWidth];
        int maxDepositValue = 0; // TODO

        Set<Integer> sortedUniqueDeposits = new TreeSet<>();
        for (int y = 0; y < this.mapHeight; y++) {
            for (int x = 0; x < this.mapWidth; x++) {
                // Don't calculate deposits for impassable locations
                if (!this.passableMap[y][x]) {
                    continue;
                }
                int value = getDepositValue(x, y, DEPOSIT_SCAN_RADIUS);
                this.depositMap[y][x] = value;
                maxDepositValue = Math.max(maxDepositValue, value);
                sortedUniqueDeposits.add(value);
            }
        }
        Integer[] sortedDeposits = sortedUniqueDeposits.toArray(new Integer[sortedUniqueDeposits.size()]);
        // System.out.println(Arrays.toString(sortedDeposits));

        // double mean = 0;
        // int total = 0;
        // for (int y = 0; y < this.mapHeight; y++) {
        //     for (int x = 0; x < this.mapWidth; x++) {
        //         if (this.depositMap[y][x] == 0) {
        //             continue;
        //         }
        //         mean += this.depositMap[y][x];
        //         total++;
        //     }
        // }
        // mean /= total;
        //
        // double variance = 0;
        // for (int y = 0; y < this.mapHeight; y++) {
        //     for (int x = 0; x < this.mapWidth; x++) {
        //         double diff = this.depositMap[y][x] - mean;
        //         variance += diff * diff;
        //     }
        // }
        // double stdev = Math.sqrt(variance / total);
        // System.out.println(stdev);
    }

    private int getDepositValue(int x, int y, int radius) {
        int value = 0;

        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                int yPos = y + i;
                int xPos = x + j;
                if (isOOB(xPos, yPos)) {
                    continue;
                }
                // Don't add impassable locations to the total
                if (!this.passableMap[yPos][xPos]) {
                    continue;
                }
                value += this.karboniteMap[yPos][xPos];
            }
        }

        return value;
    }

    private void makePods() {
        this.podsMap = new HashMap<>();
        this.podMiningTargets = new IdentityHashMap<>();
        this.podBuildingTargets = new IdentityHashMap<>();
        this.pods = new ArrayList<>();

        Set<Integer> processed = new HashSet<>();

        // Process every unit
        VecUnit myUnits = this.gc.myUnits();
        for (int i = 0; i < myUnits.size(); i++) {
            HashSet<Integer> pod = new HashSet<>();
            addToPod(myUnits.get(i), pod, processed);
            if (!pod.isEmpty()) {
                this.pods.add(pod);
            }
        }

        System.out.println(this.pods.size() + " pods.");
        for (Set<Integer> pod : this.pods) {
            System.out.println(pod);
        }
        System.out.println();
    }

    private void addToPod(Unit unit, Set<Integer> pod, Set<Integer> processed) {
        if (processed.contains(unit.id())) {
            return;
        }

        processed.add(unit.id());
        pod.add(unit.id());
        this.podsMap.put(unit.id(), pod);

        VecUnit nearbyUnits = this.gc.senseNearbyUnitsByTeam(unit.location().mapLocation(), POD_SCAN_RADIUS, this.MY_TEAM);
        for (int i = 0; i < nearbyUnits.size(); i++) {
            addToPod(nearbyUnits.get(i), pod, processed);
        }
    }

    private void assignInitialPods() {
        this.podOrders = new IdentityHashMap<>();

        if (this.pods.size() == 1) {
            this.podOrders.put(this.pods.get(0), Order.BUILD);
        } else {
            Set<Integer> bestPod = null;
            int bestPodValue = -1;
            for (Set<Integer> pod : this.pods) {
                this.podOrders.put(pod, Order.BUILD);

                int meanX = 0;
                int meanY = 0;
                for (int unit : pod) {
                    meanX += this.gc.unit(unit).location().mapLocation().getX();
                    meanY += this.gc.unit(unit).location().mapLocation().getY();
                }
                meanX /= pod.size();
                meanY /= pod.size();

                int value = getDepositValue(meanX, meanY, 5); // TODO
                if (value > bestPodValue) {
                    bestPodValue = value;
                    bestPod = pod;
                }
            }
            this.podOrders.put(bestPod, Order.MINE);
        }
    }

    /**
     * Returns the closest accessible karbonite deposit to a given location.
     *
     * @param start The location to find a karbonite deposit nearest to.
     * @return The nearest karbonite deposit that can be moved to. If none
     * exist, returns the given start location.
     */
    private MapLocation findNearestKarbonite(MapLocation start) {
        Queue<MapLocation> openSet = new LinkedList<>();
        Set<Point> closedSet = new HashSet<>();
        openSet.add(start);

        while (!openSet.isEmpty()) {
            MapLocation next = openSet.remove();
            if (this.karboniteMap[next.getY()][next.getX()] > 0) {
                return next;
            }

            closedSet.add(new Point(next.getX(), next.getY()));

            for (Direction d : DIRECTIONS) {
                MapLocation adj = next.add(d);
                int adjX = adj.getX();
                int adjY = adj.getY();
                if (isOOB(adjX, adjY) || !this.passableMap[adjY][adjX]) {
                    continue;
                }
                Point adjPoint = new Point(adj.getX(), adj.getY());
                if (!closedSet.contains(adjPoint)) {
                    openSet.add(adj);
                    closedSet.add(adjPoint);
                }
            }
        }

        return start;
    }

    @Override
    public void processTurn() {

        boolean allFactoriesProducing = true;
        for (int factory : this.myUnits.get(UnitType.Factory)) {
            Unit factoryUnit = this.allUnits.get(factory);
            if (factoryUnit.isFactoryProducing() == 0) {
                allFactoriesProducing = false;
                break;
            }
        }

        for (Set<Integer> pod : this.pods) {
            Order order = this.podOrders.get(pod);

            // Remove units that don't exist anymore
            for (Iterator<Integer> it = pod.iterator(); it.hasNext(); ) {
                int unit = it.next();
                if (!unitExists(unit)) {
                    it.remove();
                }
            }

            // TODO Handle removing from this.pods, this.podOrders, podsMap, etc.
            if (pod.isEmpty()) {
                continue;
            }

            for (int y = 0; y < this.mapHeight; y++) {
                for (int x = 0; x < this.mapWidth; x++) {
                    if (this.gc.canSenseLocation(new MapLocation(this.planet, x, y)))
                        this.karboniteMap[y][x] = (int) Math.min(this.gc.karboniteAt(new MapLocation(this.planet, x, y)), this.karboniteMap[y][x]);
                }
            }

            switch (order) {
                case BUILD:
                    Integer targetBuilding = this.podBuildingTargets.get(pod);
                    // If this pod doesn't have a building target, or if that
                    // target is built, find a new target
                    if (targetBuilding == null || this.allUnits.get(targetBuilding).structureIsBuilt() == 1) {
                        // Don't create more factories if the current ones are enough
                        if (!allFactoriesProducing) {
                            break;
                        }

                        int sampleUnit = pod.iterator().next();
                        for (Iterator<Integer> it = pod.iterator(); it.hasNext() && this.allUnits.get(sampleUnit).location().isInGarrison(); ) {
                            sampleUnit = it.next();
                        }
                        // If all units are garrisoned, don't do anything
                        if (this.allUnits.get(sampleUnit).location().isInGarrison()) {
                            break;
                        }
                        Unit blueprintingUnit = this.allUnits.get(sampleUnit);
                        MapLocation unitLoc = blueprintingUnit.location().mapLocation();
                        for (Direction d : DIRECTIONS) {
                            if (this.gc.canBlueprint(sampleUnit, UnitType.Factory, d)) {
                                this.gc.blueprint(sampleUnit, UnitType.Factory, d);
                                Unit blueprint = this.gc.senseUnitAtLocation(unitLoc.add(d));
                                this.podBuildingTargets.put(pod, blueprint.id());
                                break;
                            }
                        }
                    }
                    targetBuilding = this.podBuildingTargets.get(pod);

                    for (int unit : pod) {
                        MapLocation unitLoc = this.allUnits.get(unit).location().mapLocation();

                        if (this.gc.canBuild(unit, targetBuilding)) {
                            this.gc.build(unit, targetBuilding);
                        } else {
                            Direction toMove = this.navigator.navigate(unit, unitLoc, this.gc.unit(targetBuilding).location().mapLocation());
                            this.navigator.tryMove(unit, toMove);
                        }
                    }
                    break;
                case MINE:
                    MapLocation targetDeposit = this.podMiningTargets.get(pod);
                    // If this pod doesn't have a mining target, or if that
                    // target has run out of karbonite, find a new target
                    if (targetDeposit == null || this.karboniteMap[targetDeposit.getY()][targetDeposit.getX()] <= 0) {
                        int sampleUnit = pod.iterator().next();
                        for (Iterator<Integer> it = pod.iterator(); it.hasNext() && this.allUnits.get(sampleUnit).location().isInGarrison(); ) {
                            sampleUnit = it.next();
                        }
                        // If all units are garrisoned, don't do anything
                        if (this.allUnits.get(sampleUnit).location().isInGarrison()) {
                            break;
                        }
                        MapLocation nextDeposit = findNearestKarbonite(this.allUnits.get(sampleUnit).location().mapLocation());
                        this.podMiningTargets.put(pod, nextDeposit);
                    }
                    targetDeposit = this.podMiningTargets.get(pod);
                    this.karboniteMap[targetDeposit.getY()][targetDeposit.getX()] = (int) this.gc.karboniteAt(targetDeposit);
                    for (int unit : pod) {
                        MapLocation unitLoc = this.allUnits.get(unit).location().mapLocation();
                        Direction dirToTarget = unitLoc.directionTo(targetDeposit);
                        // Try to mine the target
                        if (this.gc.canHarvest(unit, dirToTarget)) {
                            this.gc.harvest(unit, dirToTarget);
                            this.karboniteMap[targetDeposit.getY()][targetDeposit.getX()] = (int) this.gc.karboniteAt(targetDeposit);
                        } else {
                            // If it can't be mined, move in range so that it can be
                            Direction toMove = this.navigator.navigate(unit, unitLoc, targetDeposit);
                            this.navigator.tryMove(unit, toMove);
                        }
                    }
                    break;
            }
        }

        for (int factory : this.myUnits.get(UnitType.Factory)) {
            Unit factoryUnit = this.allUnits.get(factory);
            if (factoryUnit.structureIsBuilt() == 0) {
                continue;
            }

            if (this.gc.canProduceRobot(factory, UnitType.Ranger)) {
                this.gc.produceRobot(factory, UnitType.Ranger);
            }

            // VecUnitID garrisoned = factoryUnit.structureGarrison();
            // for (int i = 0; i < garrisoned.size(); i++) {
            for (Direction d : DIRECTIONS) {
                if (this.gc.canUnload(factory, d)) {
                    this.gc.unload(factory, d);
                }
            }
            // }
        }

        for (int ranger : this.myUnits.get(UnitType.Ranger)) {
            move(ranger, new MapLocation(this.planet, 10, 10));
        }


        // int i = 0;
        // // MapLocation[] targets = {new MapLocation(this.planet, 18, 4), new MapLocation(this.planet, 18, 15)};
        // MapLocation[] targets = {new MapLocation(this.planet, 10, 10)};
        //
        // // Set<Unit> pod = new HashSet<>();
        // // for (int worker : this.myUnits.get(UnitType.Worker)) {
        // //     pod.add(this.allUnits.get(worker));
        // // }
        // // this.navigator.doNavigate(pod, targets[0]);
        //
        // for (int worker : this.myUnits.get(UnitType.Worker)) {
        //     Unit workerUnit = this.allUnits.get(worker);
        //     MapLocation loc = workerUnit.location().mapLocation();
        //
        //     MapLocation target = targets[i % targets.length];
        //
        //     Direction toMove = this.navigator.navigate(worker, loc, target);
        //     if (this.gc.isMoveReady(worker) && this.gc.canMove(worker, toMove)) {
        //         this.gc.moveRobot(worker, toMove);
        //     }
        //     i++;
        //
        //
        //     // VecUnit allUnits = this.gc.units();
        //     // for (int i = 0; i < allUnits.size(); i++) {
        //     //     Location l = allUnits.get(i).location();
        //     //     if (l.isOnMap() && !l.isInGarrison()) {
        //     //         MapLocation ml = l.mapLocation();
        //     //         navMap[ml.getY()][ml.getX()] = false;
        //     //     }
        //     // }
        //     // // for (boolean[] b : navMap) {
        //     // //     for (boolean bb : b) {
        //     // //         System.out.print(bb ? "." : "#");
        //     // //     }
        //     // // }
        //     // // System.out.println();
        //     //
        //     // Direction toMove = nav.pathfind(new Point(loc.getX(), loc.getY()), new Point(18, 10), navMap);
        //     // if (toMove != null && this.gc.isMoveReady(worker) && this.gc.canMove(worker, toMove)) {
        //     //     this.gc.moveRobot(worker, toMove);
        //     // }
        // }

        // VecUnit is a class that you can think of as similar to ArrayList<Unit>, but immutable.
        // VecUnit units = this.gc.myUnits();
        // for (int i = 0; i < units.size(); i++) {
        //     Unit unit = units.get(i);
        //
        //     Direction[][] navMap = this.navMaps.get(new Point(10, 10));
        //     MapLocation loc = unit.location().mapLocation();
        //     int unitX = loc.getX();
        //     int unitY = loc.getY();
        //     Direction nextDir = navMap[unitY][unitX];
        //     if (this.gc.isMoveReady(unit.id()) && this.gc.canMove(unit.id(), nextDir)) {
        //         this.gc.moveRobot(unit.id(), nextDir);
        //     }
        //
        // }


        /*
        for (int worker : this.myUnits.get(UnitType.Worker)) {
            // if (this.gc.karbonite() > bc.bcUnitTypeBlueprintCost(UnitType.Factory)) {
            Unit workerUnit = this.allUnits.get(worker);
            if (workerUnit.location().isInGarrison()) {
                continue;
            }
            MapLocation loc = workerUnit.location().mapLocation();

            for (int factory : this.myUnits.get(UnitType.Factory)) {
                if (this.gc.canBuild(worker, factory)) {
                    this.gc.build(worker, factory);
                }
            }

            for (Direction d : DIRECTIONS) {
                MapLocation adj = loc.add(d);
                if (!isOOB(adj.getX(), adj.getY()) && this.map[adj.getY()][adj.getX()] == 0 && this.gc.canBlueprint(worker, UnitType.Factory, d)) {
                    this.gc.blueprint(worker, UnitType.Factory, d);
                    break;
                }
                if (!isOOB(adj.getX(), adj.getY()) && this.gc.canHarvest(worker, d)) {
                    this.gc.harvest(worker, d);
                    break;
                }
            }
            // }
        }

        for (int ranger : this.myUnits.get(UnitType.Ranger)) {
            Unit rangerUnit = this.allUnits.get(ranger);
            if (rangerUnit.location().isInGarrison()) {
                continue;
            }
            MapLocation loc = rangerUnit.location().mapLocation();

            VecUnit rawTargets = this.gc.senseNearbyUnitsByType(loc, rangerUnit.attackRange(), UnitType.Factory);
            List<Unit> targets = new ArrayList<>();
            for (int i = 0; i < rawTargets.size(); i++) {
                if (rawTargets.get(i).team() == this.ENEMY_TEAM) {
                    targets.add(rawTargets.get(i));
                }
            }
            if (targets.isEmpty()) {
                rawTargets = this.gc.senseNearbyUnitsByTeam(loc, rangerUnit.attackRange(), this.ENEMY_TEAM);
                for (int i = 0; i < rawTargets.size(); i++) {
                    targets.add(rawTargets.get(i));
                }
            }
            Unit closestTarget = null;
            long closestDistance = Long.MAX_VALUE;
            for (int i = 0; i < targets.size(); i++) {
                Unit target = targets.get(i);
                MapLocation targetLoc = target.location().mapLocation();
                long distance = loc.distanceSquaredTo(targetLoc);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestTarget = target;
                }
            }
            if (closestTarget != null) {
                if (this.gc.canAttack(ranger, closestTarget.id()) && rangerUnit.attackHeat() < 10) {
                    this.gc.attack(ranger, closestTarget.id());
                }
            }
        }

        for (int factory : this.myUnits.get(UnitType.Factory)) {
            Unit factoryUnit = this.allUnits.get(factory);
            MapLocation loc = factoryUnit.location().mapLocation();

            boolean freeSurroundings = false;
            for (Direction d : DIRECTIONS) {
                if (this.gc.canUnload(factory, d)) {
                    this.gc.unload(factory, d);
                }
                MapLocation adj = loc.add(d);
                if (isOOB(adj.getX(), adj.getY())) {
                    continue;
                }
                if (this.gc.isOccupiable(adj) == 1) {
                    freeSurroundings = true;
                    break;
                }
            }
            // Don't make stuff if you can't unload it
            if (!freeSurroundings) {
                continue;
            }
            if (Math.random() < 0.5) {
                if (this.gc.canProduceRobot(factory, UnitType.Worker)) {
                    this.gc.produceRobot(factory, UnitType.Worker);
                }
            } else {
                if (this.gc.canProduceRobot(factory, UnitType.Ranger)) {
                    this.gc.produceRobot(factory, UnitType.Ranger);
                }
            }
        }

        */
    }
}
