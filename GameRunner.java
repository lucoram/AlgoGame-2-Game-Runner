import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class GameRunner {

    private static final char EMPTY_CHAR = ' ';
    private static final int EXCAVATOR_DIG_AFTER_STEP = 5;
    private static final int MACHINE_MAX_CLIFF_HEIGHT = 5;
    private static final int HERO_MAX_CLIFF_HEIGHT = 3;
    private static final int HERO_HOPPING_STAMINA_DROP = 10;
    private static final int HERO_SAME_ELEVATION_STAMINA_DROP = 1;
    private static final int HERO_UPHILL_STAMINA_DROP = 3;
    private static final int HERO_DOWNHILL_STAMINA_DROP = 0;
    private static final int HERO_CHEST_PUSHING_STAMINA_DROP = 5;

    public static final int MAX_HERO_STAMINA = 100;
    public static final int MAX_HERO_BATTERY = 100;

    private static int collectedPreciousStones = 0;
    private static int hiddenTreasureChest = 0;

    private static int[][] neighboringCellsDiff = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
    private static char[] fosaAndExcavatorAndChestBlockers = { '#', 't' };
    private static char[] makiAndGrapplerBlockers = { '#', 'o' };

    private static char[][][] map;
    private static int[][] elevation;

    private static int mapHeight;
    private static int mapWidth;
    private static int mapTimeLimit;

    private static List<String> heroesActions;

    private static Hero ikotofosa;
    private static Hero imahaki;

    private static List<Machine> machines;

    private static Set<AgentAction> validationLessMachineActions;
    private static Set<AgentAction> heroesMoveActions;
    private static Set<AgentAction> heroesPushActions;

    static {
        validationLessMachineActions = new HashSet<>();
        validationLessMachineActions.add(AgentAction.WAIT);
        validationLessMachineActions.add(AgentAction.CW);
        validationLessMachineActions.add(AgentAction.CCW);

        heroesMoveActions = new HashSet<>();
        heroesMoveActions.add(AgentAction.UP);
        heroesMoveActions.add(AgentAction.DOWN);
        heroesMoveActions.add(AgentAction.LEFT);
        heroesMoveActions.add(AgentAction.RIGHT);

        heroesPushActions = new HashSet<>();
        heroesPushActions.add(AgentAction.PUSH_UP);
        heroesPushActions.add(AgentAction.PUSH_DOWN);
        heroesPushActions.add(AgentAction.PUSH_LEFT);
        heroesPushActions.add(AgentAction.PUSH_RIGHT);
    }

    public static void main(String[] args) throws FileNotFoundException, InterruptedException {
        readMapAndInstanciateAgents();
        readHeroesActions();
        runGameLoop();
    }

    private static void runGameLoop() throws InterruptedException {
        int tickNumber = 0;

        printMapAndStats(tickNumber);

        while (true) {
            boolean timeLimitExceeded = tickNumber >= mapTimeLimit;
            boolean actionsFileIncomplete = tickNumber >= heroesActions.size();
            boolean endGame = !actionsFileIncomplete && heroesActions.get(tickNumber).equals("END_GAME");

            if (timeLimitExceeded || actionsFileIncomplete || endGame) {
                int totalScore = calcTotalScore(tickNumber);

                System.out.println("\n---------------------------------");
                String endReason = timeLimitExceeded ? "TIME LIMIT EXCEEDED" : null;
                endReason = endReason == null && actionsFileIncomplete ? "/!\\ ACTIONS FILE INCOMPLETE" : endReason;
                endReason = endReason == null && endGame ? "GAME ENDED" : endReason;

                System.out.println(endReason + ", TOTAL SCORE = " + totalScore);
                System.out.println("---------------------------------\n");

                break;
            }

            // for each agent, validate moves, starting with the heroes
            String[] actions = heroesActions.get(tickNumber).split(" \\| ");

            validateHeroMove(ikotofosa, actions[0]);
            validateHeroMove(imahaki, actions[1]);

            for (Machine machine : machines) {
                validateMachineMove(machine);
            }

            // after move validation, execute moves
            executeHeroMove(ikotofosa);
            executeHeroMove(imahaki);

            for (Machine machine : machines) {
                executeMachineMove(machine);
            }

            tickNumber++;

            // after move execution, print map and game stats
            printMapAndStats(tickNumber);
        }
    }

    private static int calcTotalScore(int iterCount) {
        int totalScore = hiddenTreasureChest * 150;
        totalScore += (collectedPreciousStones * 25);
        totalScore += ((ikotofosa.stamina + imahaki.stamina + ikotofosa.battery + imahaki.battery) / 4);
        totalScore += (mapTimeLimit - iterCount) / 2;

        return totalScore;
    }

    private static void readHeroesActions() throws FileNotFoundException {
        Scanner scan = new Scanner(new File("actions.txt"));

        heroesActions = new ArrayList<>();

        while (scan.hasNextLine()) {
            heroesActions.add(scan.nextLine());
        }

        scan.close();
    }

    private static void readMapAndInstanciateAgents() throws FileNotFoundException {
        Scanner mapScan = new Scanner(new File("map.txt"));
        Scanner elevationScan = new Scanner(new File("elevation.txt"));

        mapHeight = mapScan.nextInt();
        mapWidth = mapScan.nextInt();
        mapTimeLimit = mapScan.nextInt();

        mapScan.nextLine();

        initEmptyMap();

        machines = new ArrayList<>();

        ikotofosa = new Hero();
        imahaki = new Hero();

        for (int row = 0; row < mapHeight; row++) {
            String mapLine = mapScan.nextLine();
            String elevationLine = elevationScan.nextLine();

            for (int col = 0; col < mapWidth; col++) {
                char mapCellValue = mapLine.charAt(col);
                int elevationCellValue = (elevationLine.charAt(col) - '0');

                elevation[row][col] = elevationCellValue;

                if (mapCellValue == 'F' || mapCellValue == 'M') {
                    initHero(mapCellValue, row, col);

                    continue;
                }

                if (mapCellValue == 'X' || mapCellValue == 'G') {
                    initMachine(mapCellValue, row, col);

                    continue;
                }

                if (mapCellValue == '*') {
                    map[row][col][2] = '*';
                    map[row][col][3] = '.';

                    continue;
                }

                map[row][col][3] = mapCellValue;
            }
        }

        mapScan.close();
        elevationScan.close();
    }

    private static void initHero(char type, int row, int col) {
        Hero hero = (type == 'F' ? ikotofosa : imahaki);
        hero.currentPositionRow = row;
        hero.currentPositionCol = col;
        hero.type = type;
        hero.mapLevelIndex = (type == 'F' ? 0 : 1);
        hero.requestedAction = AgentAction.WAIT;
        hero.validatedAction = AgentAction.WAIT;

        map[row][col][hero.mapLevelIndex] = type;
        map[row][col][3] = '.';
    }

    private static void initMachine(char type, int row, int col) {
        Machine machine = new Machine();
        machine.currentPositionRow = row;
        machine.currentPositionCol = col;
        machine.type = type;
        machine.currentOrientation = (type == 'X' ? MachineOrientation.FACING_LEFT
                : MachineOrientation.FACING_RIGHT);
        machine.requestedAction = AgentAction.WAIT;
        machine.validatedAction = AgentAction.WAIT;

        map[row][col][2] = type;
        map[row][col][3] = '.';

        machines.add(machine);
    }

    private static void initEmptyMap() {

        // level 0: Ikotofosa, 1: Imahaki, 2: machines and chest, 3: rocks, trees,
        // holes, bushes and precious stones and empty spaces
        map = new char[mapHeight][mapWidth][4];
        elevation = new int[mapHeight][mapWidth];

        for (int row = 0; row < mapHeight; row++) {
            for (int col = 0; col < mapWidth; col++) {
                Arrays.fill(map[row][col], EMPTY_CHAR);
            }
        }
    }

    private static void printMapAndStats(int iter) throws InterruptedException {
        clearConsole();

        System.out.println("Iteration nb : " + iter);

        System.out.println("\n---- Heroes ---\n");
        System.out.println("Ikotofosa : pos = (" + ikotofosa.currentPositionRow + "," + ikotofosa.currentPositionCol
                + "), requested action = " + ikotofosa.requestedAction.name() + ", executed action : "
                + ikotofosa.validatedAction.name() + ", stamina = " + ikotofosa.stamina + ", battery = "
                + ikotofosa.battery + ", collected stones : " + ikotofosa.collectedStones);
        System.out.println("Imahaki : pos = (" + imahaki.currentPositionRow + "," + imahaki.currentPositionCol
                + "), requested action = " + imahaki.requestedAction.name() + ", executed action : "
                + imahaki.validatedAction.name() + ", stamina = " + imahaki.stamina + ", battery = "
                + imahaki.battery + ", collected stones : " + imahaki.collectedStones);

        System.out.println("\n---- Machines ---\n");
        for (Machine machine : machines) {
            System.out.println(machine.type + " : pos = (" + machine.currentPositionRow + ","
                    + machine.currentPositionCol + "), orient = "
                    + machine.currentOrientation.name() + ", requested action : "
                    + (machine.requestedAction != null ? machine.requestedAction.name() : "[AUTO_MOVE]")
                    + ", executed action : " + machine.validatedAction.name() + ", collected stones : "
                    + machine.collectedStones);

            // TODO move this to reset func
            machine.requestedAction = null;
            machine.validatedAction = null;
        }

        System.out.println("\n-----------");

        // TODO calc at game end
        collectedPreciousStones = ikotofosa.collectedStones + imahaki.collectedStones;

        System.out.println("TOTAL STONES : " + collectedPreciousStones + ", TOTAL CHESTS : " + hiddenTreasureChest);

        System.out.println("-----------\n");

        for (int row = 0; row < mapHeight; row++) {
            char[] line = buildLineChars(row);
            System.out.println(joinLineChars(line));
            System.out.println();
        }

        // Thread.sleep(100);
        // Thread.sleep(2000);
        // Thread.sleep(1000);
        // Thread.sleep(500);
        Thread.sleep(200);
    }

    private static char[] buildLineChars(int row) {
        char[] line = new char[mapWidth];

        for (int col = 0; col < mapWidth; col++) {
            for (int level = 0; level < 4; level++) {
                char currChar = map[row][col][level];

                if (currChar != EMPTY_CHAR) {
                    line[col] = currChar;

                    break;
                }
            }
        }

        return line;
    }

    private static void clearConsole() {
        System.out.print("\033\143");
    }

    private static String joinLineChars(char[] line) {
        StringBuilder builder = new StringBuilder();

        for (char c : line) {
            builder.append(c).append(" ");
        }

        return builder.toString();
    }

    private static void validateHeroMove(Hero hero, String actionName) {
        AgentAction action = AgentAction.valueOf(actionName);

        hero.requestedAction = action;

        if (action == null || action == AgentAction.WAIT) {
            hero.validatedAction = AgentAction.WAIT;

            if (hero.hackedMachine != null && !hero.hackedMachine.isMoveQueued) {
                hero.hackedMachine.requestedAction = AgentAction.WAIT;
            }

            return;
        }

        if (heroesMoveActions.contains(action)) {
            hero.nextPositionRow = hero.currentPositionRow;
            hero.nextPositionCol = hero.currentPositionCol;

            hero.amendNextPositionCoordByMoveDir(action);

            // Check for blockers
            char[] heroBlockers = (hero.type == 'F' ? fosaAndExcavatorAndChestBlockers : makiAndGrapplerBlockers);

            if (cellHasBlockers(hero.nextPositionRow, hero.nextPositionCol, heroBlockers)
                    || isNextCellUnreachable(hero)) {
                hero.validatedAction = AgentAction.WAIT;

                return;
            }

            // check for skippable prop
            char prop = map[hero.nextPositionRow][hero.nextPositionCol][3];
            if (prop == 'o' || prop == 't') {

                // can't execute hopping over prop if not enough stamina for doing it
                if (hero.stamina < HERO_HOPPING_STAMINA_DROP) {
                    hero.validatedAction = AgentAction.WAIT;

                    return;
                }

                hero.amendNextPositionCoordByMoveDir(action);

                if (cellHasBlockers(hero.nextPositionRow, hero.nextPositionCol, fosaAndExcavatorAndChestBlockers)
                        || cellHasBlockers(hero.nextPositionRow, hero.nextPositionCol, makiAndGrapplerBlockers)) {
                    hero.validatedAction = AgentAction.WAIT;

                    return;
                }
            }

            hero.validatedAction = action;

            if (hero.hackedMachine != null) {
                hero.hackedMachine.requestedAction = AgentAction.WAIT;
                hero.hackedMachine.isBeingHacked = false;
                hero.hackedMachine.isMoveQueued = false;
                hero.hackedMachine.hackingHero = null;
                hero.hackedMachine = null;
            }

            return;
        }

        if (heroesPushActions.contains(action)) {
            // can't execute push move while hacking a machine
            if (hero.hackedMachine != null || hero.stamina < HERO_CHEST_PUSHING_STAMINA_DROP) {
                hero.validatedAction = AgentAction.WAIT;

                return;
            }

            hero.nextPositionRow = hero.currentPositionRow;
            hero.nextPositionCol = hero.currentPositionCol;

            String heroMoveWithPushActionName = action.name().replace("PUSH_", "");
            AgentAction heroMoveWithPushAction = AgentAction.valueOf(heroMoveWithPushActionName);

            hero.amendNextPositionCoordByMoveDir(heroMoveWithPushAction);

            int otherHeroMapLevelIndex = hero.type == 'F' ? 1 : 0;

            if (!cellHasChest(hero.nextPositionRow, hero.nextPositionCol) || isNextCellUnreachable(hero)
                    || chestIsLockedByOtherHero(hero, otherHeroMapLevelIndex)) {
                hero.validatedAction = AgentAction.WAIT;

                return;
            }

            int[] chestNextPositionRowCoords = hero.amendNextPositionCoordByMoveDir(heroMoveWithPushAction,
                    hero.nextPositionRow, hero.nextPositionCol);
            int chestNextPositionRow = chestNextPositionRowCoords[0];
            int chestNextPositionCol = chestNextPositionRowCoords[1];

            if (cellHasBlockers(chestNextPositionRow, chestNextPositionCol, fosaAndExcavatorAndChestBlockers)
                    || cellHasChest(chestNextPositionRow, chestNextPositionCol)
                    || cellHasMachine(chestNextPositionRow, chestNextPositionCol)
                    || cellIsAtTop(chestNextPositionRow, chestNextPositionCol, hero.nextPositionRow,
                            hero.nextPositionCol)) {
                hero.validatedAction = AgentAction.WAIT;

                return;
            }

            hero.validatedAction = action;

            return;
        }

        // hack actions
        hero.validatedAction = AgentAction.WAIT;

        if (hero.battery == 0) {
            return;
        }

        hero.battery--;

        if (action == AgentAction.HACK) {
            Machine machineOnSameCell = getMachineOnTheCell(hero.type, hero.currentPositionRow,
                    hero.currentPositionCol);

            if (machineOnSameCell != null) {
                machineOnSameCell.isBeingHacked = true;
                machineOnSameCell.isMoveQueued = false;
                machineOnSameCell.requestedAction = AgentAction.WAIT;
                machineOnSameCell.hackingHero = hero;
                hero.hackedMachine = machineOnSameCell;
                hero.hackedMachine.autoStepCount = 0;
            }

            return;
        }

        if (hero.hackedMachine == null) {
            return;
        }

        String machineActionNameByHack = action.name().replace("HACK_", "");
        hero.hackedMachine.requestedAction = AgentAction.valueOf(machineActionNameByHack);
    }

    private static boolean chestIsLockedByOtherHero(Hero hero, int otherHeroMapLevelIndex) {
        int nextPositionRow = hero.nextPositionRow;
        int nextPositionCol = hero.nextPositionCol;
        int heroMapLevelIndex = hero.mapLevelIndex;
        char otherHeroCellValue = map[nextPositionRow][nextPositionCol][otherHeroMapLevelIndex];

        if (otherHeroCellValue != 'c') {
            map[nextPositionRow][nextPositionCol][heroMapLevelIndex] = 'c';

            return false;
        }

        return true;
    }

    private static Machine getMachineOnTheCell(char heroType, int heroPositionRow, int heroPositionCol) {
        for (Machine machine : getHackableMachines(heroType)) {
            if (machine.currentPositionRow == heroPositionRow && machine.currentPositionCol == heroPositionCol) {
                return machine;
            }
        }

        return null;
    }

    private static List<Machine> getHackableMachines(char heroType) {
        List<Machine> hackableMachines = new ArrayList<>();

        for (Machine machine : machines) {
            if (heroType == 'F' && machine.type == 'X' || heroType == 'M' && machine.type == 'G') {
                hackableMachines.add(machine);
            }
        }

        return hackableMachines;
    }

    private static boolean isNextCellUnreachable(Hero hero) {
        int nextMoveRow = hero.nextPositionRow;
        int nextMoveCol = hero.nextPositionCol;

        // Modify next cell by non blocker prop for hero
        if (map[nextMoveRow][nextMoveCol][3] == (hero.type == 'F' ? 'o' : 't')) {

            int[] amendedNextPositionCoords = hero.amendNextPositionCoordByMoveDir(hero.requestedAction, nextMoveRow,
                    nextMoveCol);
            nextMoveRow = amendedNextPositionCoords[0];
            nextMoveCol = amendedNextPositionCoords[1];

            char[] cellBlockers = (hero.type == 'F' ? fosaAndExcavatorAndChestBlockers : makiAndGrapplerBlockers);
            if (cellHasBlockers(nextMoveRow, nextMoveCol, cellBlockers)) {
                return true;
            }
        }

        int currPosRow = hero.currentPositionRow;
        int currPosCol = hero.currentPositionCol;

        int currPosToNextLevelDiff = elevation[currPosRow][currPosCol] - elevation[nextMoveRow][nextMoveCol];
        int nextPosToCurrLevelDiff = elevation[nextMoveRow][nextMoveCol] - elevation[currPosRow][currPosCol];

        boolean nextCellIsAtTop = cellIsAtTop(nextMoveRow, nextMoveCol, currPosRow, currPosCol);
        boolean currCellIsAtTop = cellIsAtTop(currPosRow, currPosCol, nextMoveRow, nextMoveCol);
        boolean nextAndCurrAtSameElevation = currPosToNextLevelDiff == 0;

        if ((nextCellIsAtTop
                && (hero.stamina < HERO_UPHILL_STAMINA_DROP || nextPosToCurrLevelDiff >= HERO_MAX_CLIFF_HEIGHT))
                || (nextAndCurrAtSameElevation && hero.stamina < HERO_SAME_ELEVATION_STAMINA_DROP)
                || (currCellIsAtTop && currPosToNextLevelDiff >= MACHINE_MAX_CLIFF_HEIGHT)) {
            return true;
        }

        return false;
    }

    private static void validateMachineMove(Machine machine) {
        if (machine.type == 'G' && cellHasTree(machine)) {
            machine.isMoveQueued = false;
            machine.validatedAction = AgentAction.CUT;

            return;
        }

        if (validationLessMachineActions.contains(machine.requestedAction)) {
            machine.isMoveQueued = false;
            machine.validatedAction = machine.requestedAction;

            return;
        }

        if (machine.requestedAction == AgentAction.FILL) {
            machine.isMoveQueued = false;
            machine.validatedAction = (cellHasHole(machine) ? AgentAction.FILL : AgentAction.WAIT);

            return;
        }

        if (!machine.isBeingHacked && machine.type == 'X' && machine.autoStepCount == EXCAVATOR_DIG_AFTER_STEP) {
            machine.isMoveQueued = false;
            machine.validatedAction = AgentAction.DIG;
            machine.autoStepCount = 0;

            return;
        }

        machine.nextPositionRow = machine.currentPositionRow;
        machine.nextPositionCol = machine.currentPositionCol;

        AgentAction moveDirection = machine.getMoveDirectionByOrientation();
        machine.amendNextPositionCoordByMoveDir(moveDirection);

        // check for blockers
        char[] machineBlockers = (machine.type == 'X' ? fosaAndExcavatorAndChestBlockers : makiAndGrapplerBlockers);
        boolean isNextCellBlocker = cellHasBlockers(machine.nextPositionRow, machine.nextPositionCol, machineBlockers);

        // check for other machines
        boolean isNextCellOccupiedOrRiskOccupation = cellHasOrRiskToHaveMachine(machine);

        // check for a chest and the risk of it being blocked
        boolean isNextCellChestAndRiskBeingBlocked = cellHasChestAndRiskBeingBlocked(machine);

        boolean isNextCellNextToRiskyChest = cellIsNextToChestThatRisksBeingPushedInto(machine);

        // check for high cliff
        boolean isNextCellOnACliff = cellIsOnCliff(machine.currentPositionRow, machine.currentPositionCol,
                machine.nextPositionRow, machine.nextPositionCol, MACHINE_MAX_CLIFF_HEIGHT);

        if (isNextCellBlocker || isNextCellOccupiedOrRiskOccupation || isNextCellChestAndRiskBeingBlocked
                || isNextCellNextToRiskyChest || isNextCellOnACliff) {
            machine.validatedAction = (machine.isBeingHacked ? AgentAction.WAIT
                    : (machine.type == 'X' ? AgentAction.CCW : AgentAction.CW));
            machine.isMoveQueued = false;

            return;
        }

        machine.validatedAction = (machine.isMoveQueued ? AgentAction.MOVE : AgentAction.QUEUE_MOVE);
    }

    private static boolean cellIsOnCliff(int currPosRow, int currPosCol, int nextMoveRow, int nextMoveCol,
            int maxCliffHeight) {
        return cellIsValid(currPosRow, currPosCol) && cellIsValid(nextMoveRow, nextMoveCol)
                && Math.abs(elevation[currPosRow][currPosCol] - elevation[nextMoveRow][nextMoveCol]) >= maxCliffHeight;
    }

    private static boolean cellHasTree(Machine machine) {
        return map[machine.currentPositionRow][machine.currentPositionCol][3] == 't';
    }

    private static boolean cellHasHole(Machine machine) {
        return map[machine.currentPositionRow][machine.currentPositionCol][3] == 'o';
    }

    private static boolean cellHasBlockers(int row, int col, char[] blockers) {
        if (!cellIsValid(row, col)) {
            return true;
        }

        for (char blocker : blockers) {
            if (map[row][col][3] == blocker) {
                return true;
            }
        }

        return false;
    }

    private static boolean cellHasOrRiskToHaveMachine(Machine machine) {

        if (cellHasMachine(machine.nextPositionRow, machine.nextPositionCol)) {
            return true;
        }

        for (int[] diff : neighboringCellsDiff) {
            int dRow = machine.nextPositionRow + diff[0];
            int dCol = machine.nextPositionCol + diff[1];

            if (dRow == machine.currentPositionRow && dCol == machine.currentPositionCol) {
                continue;
            }

            if (cellHasMachine(dRow, dCol)) {
                return true;
            }
        }

        return false;
    }

    private static boolean cellHasChestAndRiskBeingBlocked(Machine machine) {
        if (!cellHasChest(machine.nextPositionRow, machine.nextPositionCol)) {
            return false;
        }

        for (int[] diff : neighboringCellsDiff) {
            int dRow = machine.nextPositionRow + diff[0];
            int dCol = machine.nextPositionCol + diff[1];

            if (dRow == machine.currentPositionRow && dCol == machine.currentPositionCol) {
                continue;
            }

            if (cellHasHero(dRow, dCol)) {
                return true;
            }
        }

        AgentAction machineMoveDirAction = machine.getMoveDirectionByOrientation();
        int[] secondNextMoveCoords = machine.amendNextPositionCoordByMoveDir(machineMoveDirAction,
                machine.nextPositionRow, machine.nextPositionCol);
        int secondNextMoveRow = secondNextMoveCoords[0];
        int secondNextMoveCol = secondNextMoveCoords[1];

        if (cellHasBlockers(secondNextMoveRow, secondNextMoveCol, fosaAndExcavatorAndChestBlockers)
                || cellHasChest(secondNextMoveRow, secondNextMoveCol)) {
            return true;
        }

        if (cellIsOnCliff(machine.nextPositionRow, machine.nextPositionCol, secondNextMoveRow, secondNextMoveCol,
                HERO_MAX_CLIFF_HEIGHT)
                && cellIsAtTop(secondNextMoveRow, secondNextMoveCol, machine.nextPositionRow,
                        machine.nextPositionCol)) {
            return true;
        }

        for (int[] diff : neighboringCellsDiff) {
            int dRow = secondNextMoveRow + diff[0];
            int dCol = secondNextMoveCol + diff[1];

            if (dRow == machine.nextPositionRow && dCol == machine.nextPositionCol) {
                continue;
            }

            if (cellHasMachine(dRow, dCol)) {
                return true;
            }
        }

        return false;
    }

    private static boolean cellIsNextToChestThatRisksBeingPushedInto(Machine machine) {

        for (int[] diff : neighboringCellsDiff) {
            int dRow = machine.nextPositionRow + diff[0];
            int dCol = machine.nextPositionCol + diff[1];

            if (dRow == machine.currentPositionRow && dCol == machine.currentPositionCol) {
                continue;
            }

            if (cellHasChest(dRow, dCol)) {
                int nextDRow = dRow + diff[0];
                int nextDCol = dCol + diff[1];

                if (cellHasHero(nextDRow, nextDCol)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean cellIsAtTop(int currPosRow, int currPosCol, int nextMoveRow, int nextMoveCol) {
        int currElevation = elevation[currPosRow][currPosCol];
        int nextElevation = elevation[nextMoveRow][nextMoveCol];

        return currElevation > nextElevation;
    }

    private static boolean cellHasChest(int row, int col) {
        return cellIsValid(row, col) && map[row][col][2] == '*';
    }

    private static boolean cellHasMachine(int row, int col) {
        boolean cellIsValid = cellIsValid(row, col);

        boolean cellHasMachine = cellIsValid && map[row][col][2] == 'X';
        cellHasMachine = cellHasMachine || (cellIsValid && map[row][col][2] == 'G');

        return cellHasMachine;
    }

    private static boolean cellHasHero(int row, int col) {
        boolean cellIsValid = cellIsValid(row, col);

        boolean cellHasHero = cellIsValid && map[row][col][0] == 'F';
        cellHasHero = cellHasHero || (cellIsValid && map[row][col][1] == 'M');

        return cellHasHero;
    }

    private static boolean cellIsValid(int row, int col) {
        return row >= 0 && row < mapHeight && col >= 0 && col < mapWidth;
    }

    private static void executeMachineMove(Machine machine) {
        AgentAction machineValidatedAction = machine.validatedAction;

        int machineCurrRow = machine.currentPositionRow;
        int machineCurrCol = machine.currentPositionCol;

        if (machineValidatedAction == AgentAction.WAIT) {
            return;
        }

        if (machineValidatedAction == AgentAction.QUEUE_MOVE) {
            machine.isMoveQueued = true;

            return;
        }

        machine.isMoveQueued = false;

        if (machineValidatedAction == AgentAction.CCW || machineValidatedAction == AgentAction.CW) {
            machine.rotateMachine(machineValidatedAction);

            return;
        }

        if (machineValidatedAction == AgentAction.DIG) {
            if (map[machineCurrRow][machineCurrCol][3] != '@') {
                map[machineCurrRow][machineCurrCol][3] = 'o';
            }

            return;
        }

        if (machineValidatedAction == AgentAction.CUT || machineValidatedAction == AgentAction.FILL) {
            map[machineCurrRow][machineCurrCol][3] = '.';

            return;
        }

        doMoveMachine(machine);
    }

    private static void doMoveMachine(Machine machine) {

        int machineCurrRow = machine.currentPositionRow;
        int machineCurrCol = machine.currentPositionCol;

        map[machineCurrRow][machineCurrCol][2] = EMPTY_CHAR;

        machineCurrRow = machine.nextPositionRow;
        machineCurrCol = machine.nextPositionCol;

        if (map[machineCurrRow][machineCurrCol][2] == '*') {
            AgentAction machineMoveAction = machine.getMoveDirectionByOrientation();
            doMoveChest(machine, machineMoveAction, machineCurrRow, machineCurrCol);
        }

        if (map[machineCurrRow][machineCurrCol][3] == '+') {
            machine.collectedStones++;
            map[machineCurrRow][machineCurrCol][3] = '.';
        }

        map[machineCurrRow][machineCurrCol][2] = machine.type;
        machine.currentPositionRow = machineCurrRow;
        machine.currentPositionCol = machineCurrCol;

        if (!machine.isBeingHacked) {
            machine.autoStepCount++;
        } else {
            Hero hero = machine.hackingHero;
            int heroMapLevel = hero.type == 'F' ? 0 : 1;

            map[hero.currentPositionRow][hero.currentPositionCol][heroMapLevel] = EMPTY_CHAR;
            hero.currentPositionRow = machine.currentPositionRow;
            hero.currentPositionCol = machine.currentPositionCol;
            map[hero.currentPositionRow][hero.currentPositionCol][heroMapLevel] = hero.type;
        }

        giveCollectedStonesToHero(machine, machineCurrRow, machineCurrCol);

        // TODO move to reset func
        // machine.requestedAction = null;
        // machine.validatedAction = null;
    }

    private static void giveCollectedStonesToHero(Machine machine, int machineCurrRow, int machineCurrCol) {
        char ikotofosaLevelCell = map[machineCurrRow][machineCurrCol][0];
        char imahakiLevelCell = map[machineCurrRow][machineCurrCol][1];

        Hero heroInCell = (ikotofosaLevelCell != EMPTY_CHAR ? ikotofosa
                : imahakiLevelCell != EMPTY_CHAR ? imahaki : null);

        if (heroInCell != null) {
            heroInCell.collectedStones += machine.collectedStones;
            machine.collectedStones = 0;
        }
    }

    private static void executeHeroMove(Hero hero) {
        int currPosRow = hero.currentPositionRow;
        int currPosCol = hero.currentPositionCol;
        int mapLevelIndex = hero.mapLevelIndex;

        if (heroesMoveActions.contains(hero.validatedAction)) {
            int destPosRow = hero.nextPositionRow;
            int destPosCol = hero.nextPositionCol;

            map[currPosRow][currPosCol][mapLevelIndex] = EMPTY_CHAR;
            map[destPosRow][destPosCol][mapLevelIndex] = hero.type;

            boolean isHeroHoppingOverProp = Math.abs(destPosRow - currPosRow) > 1
                    || Math.abs(destPosCol - currPosCol) > 1;

            if (map[destPosRow][destPosCol][3] == '+') {
                hero.collectedStones++;
                map[destPosRow][destPosCol][3] = '.';
            }

            stealCollectedStonesFromMachine(hero, destPosRow, destPosCol);

            int moveStaminaDrop = HERO_SAME_ELEVATION_STAMINA_DROP;

            if (isHeroHoppingOverProp) {
                moveStaminaDrop = HERO_HOPPING_STAMINA_DROP;
            } else if (cellIsAtTop(currPosRow, currPosCol, destPosRow, destPosCol)) {
                moveStaminaDrop = HERO_DOWNHILL_STAMINA_DROP;
            } else if (cellIsAtTop(destPosRow, destPosCol, currPosRow, currPosCol)) {
                moveStaminaDrop = HERO_UPHILL_STAMINA_DROP;
            }

            hero.stamina -= moveStaminaDrop;
            hero.currentPositionRow = destPosRow;
            hero.currentPositionCol = destPosCol;

            hero.stamitaWaitRecoverCount = 0;

            return;
        }

        if (hero.validatedAction == AgentAction.WAIT) {
            hero.stamitaWaitRecoverCount++;

            if (hero.stamitaWaitRecoverCount == 2) {
                hero.stamina = Math.min(hero.stamina + 1, MAX_HERO_STAMINA);
                hero.stamitaWaitRecoverCount = 0;
            }

            return;
        }

        if (heroesPushActions.contains(hero.validatedAction)) {
            int destPosRow = hero.nextPositionRow;
            int destPosCol = hero.nextPositionCol;

            map[currPosRow][currPosCol][mapLevelIndex] = EMPTY_CHAR;
            map[destPosRow][destPosCol][mapLevelIndex] = hero.type;

            if (map[destPosRow][destPosCol][3] == '+') {
                hero.collectedStones++;
                map[destPosRow][destPosCol][3] = '.';
            }

            String heroMoveWithPushActionName = hero.validatedAction.name().replace("PUSH_", "");
            AgentAction heroMoveWithPushAction = AgentAction.valueOf(heroMoveWithPushActionName);
            doMoveChest(hero, heroMoveWithPushAction, hero.nextPositionRow, hero.nextPositionCol);

            map[hero.nextPositionRow][hero.nextPositionCol][2] = '.';

            hero.stamina -= HERO_CHEST_PUSHING_STAMINA_DROP;
            hero.currentPositionRow = destPosRow;
            hero.currentPositionCol = destPosCol;

            hero.stamitaWaitRecoverCount = 0;
        }
    }

    private static void doMoveChest(Agent pusher, AgentAction moveAction, int pusherCurrRow, int pusherCurrCol) {
        int[] newChestCoords = pusher.amendNextPositionCoordByMoveDir(moveAction, pusherCurrRow,
                pusherCurrCol);

        int newChestRow = newChestCoords[0];
        int newChestCol = newChestCoords[1];

        boolean cellIsOnCliff = cellIsOnCliff(pusherCurrRow, pusherCurrCol, newChestRow, newChestCol,
                MACHINE_MAX_CLIFF_HEIGHT);

        if (map[newChestRow][newChestCol][3] != 'o' && !cellIsOnCliff) {
            if (map[newChestRow][newChestCol][3] == '@') {
                hiddenTreasureChest++;
            } else {
                map[newChestRow][newChestCol][2] = '*';
            }
        }
    }

    private static void stealCollectedStonesFromMachine(Hero hero, int row, int col) {
        doStealPreciousStoneFromMachines(hero, row, col, machines);
    }

    private static void doStealPreciousStoneFromMachines(Hero hero, int row, int col, List<Machine> machines) {
        for (Machine machine : machines) {
            if (machine.currentPositionRow == row && machine.currentPositionCol == col) {
                hero.collectedStones += machine.collectedStones;
                machine.collectedStones = 0;
            }
        }
    }
}

abstract class Agent {
    int currentPositionRow;
    int currentPositionCol;

    int nextPositionRow;
    int nextPositionCol;

    int collectedStones;

    char type;

    AgentAction requestedAction;
    AgentAction validatedAction;

    void amendNextPositionCoordByMoveDir(AgentAction moveDirection) {
        int[] amendedPositionCoord = amendNextPositionCoordByMoveDir(moveDirection, nextPositionRow, nextPositionCol);

        nextPositionRow = amendedPositionCoord[0];
        nextPositionCol = amendedPositionCoord[1];
    }

    @SuppressWarnings("incomplete-switch")
    int[] amendNextPositionCoordByMoveDir(AgentAction moveDirection, int posRow, int posCol) {

        switch (moveDirection) {
            case UP:
                posRow--;
                break;
            case DOWN:
                posRow++;
                break;
            case LEFT:
                posCol--;
                break;
            case RIGHT:
                posCol++;
                break;
        }

        return new int[] { posRow, posCol };
    }
}

class Hero extends Agent {
    int stamina = GameRunner.MAX_HERO_STAMINA;
    int battery = GameRunner.MAX_HERO_BATTERY;
    int stamitaWaitRecoverCount;

    int mapLevelIndex;

    Machine hackedMachine;
}

class Machine extends Agent {
    int autoStepCount;

    boolean isBeingHacked;
    boolean isMoveQueued;

    Hero hackingHero;

    MachineOrientation currentOrientation;

    AgentAction getMoveDirectionByOrientation() {
        switch (currentOrientation) {
            case FACING_UP:
                return AgentAction.UP;
            case FACING_DOWN:
                return AgentAction.DOWN;
            case FACING_LEFT:
                return AgentAction.LEFT;
            case FACING_RIGHT:
                return AgentAction.RIGHT;
        }

        return null;
    }

    void rotateMachine(AgentAction rotationDirection) {
        switch (currentOrientation) {
            case FACING_UP:
                currentOrientation = (rotationDirection == AgentAction.CCW ? MachineOrientation.FACING_LEFT
                        : MachineOrientation.FACING_RIGHT);
                break;
            case FACING_DOWN:
                currentOrientation = (rotationDirection == AgentAction.CCW ? MachineOrientation.FACING_RIGHT
                        : MachineOrientation.FACING_LEFT);
                break;
            case FACING_LEFT:
                currentOrientation = (rotationDirection == AgentAction.CCW ? MachineOrientation.FACING_DOWN
                        : MachineOrientation.FACING_UP);
                break;
            case FACING_RIGHT:
                currentOrientation = (rotationDirection == AgentAction.CCW ? MachineOrientation.FACING_UP
                        : MachineOrientation.FACING_DOWN);
                break;
        }
    }
}

enum AgentAction {
    // Shared actions
    WAIT,

    // Heroes actions
    UP,
    DOWN,
    LEFT,
    RIGHT,
    PUSH_UP,
    PUSH_DOWN,
    PUSH_LEFT,
    PUSH_RIGHT,
    HACK,
    HACK_MOVE,
    HACK_FILL,
    HACK_CW,
    HACK_CCW,

    // Machines actions
    CW,
    CCW,
    MOVE,
    QUEUE_MOVE,
    DIG,
    FILL,
    CUT;
}

enum MachineOrientation {
    FACING_UP,
    FACING_DOWN,
    FACING_LEFT,
    FACING_RIGHT;
}