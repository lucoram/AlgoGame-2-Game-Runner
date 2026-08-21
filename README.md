__What is AlgoGames ?__

AlgoGames is a mix of Competitive Programming and a Game Jam. The concept is similar to [codingame](www.codingame.com)'s bot programming contests, with the twist that it's actually the participants who build the games from scratch.

__AlgoGames 2 concept__

For this second edition, participants work in teams and are asked to develop a game by following a Gameplay Design Document (GDD). The GDD describes the algorithms of AI agents that move autonomously to achieve some goals and score points.

__AlgoGames 2 game runner__

This repository contains the _game runner_ for AlgoGames 2 games. It will be used to evaluate the algorithms of the games developed by the participants.
The runner itself is a one big java main program, which reads three files: 

- `map.txt`: the ascii representation of a map that is loaded into and played in the games
- `elevation.txt`: elevation data of the above map, which contains the altitude level of each tile of the map
- `actions.txt`: a file that contains the list of actions that were performed by the heroes of the game during a run in the games themselves

_How it works? (for game jamers only)_

1. Create a map file with all the required elements to run a game, create another file that contains the related elevation data
2. Load the map and the elevation data into your game, then run it to get the actions file
3. Put the map, the elevation and the actions files in the same folder as the `GameRunner`
4. Run the game runner, wait until the end. It will render the game run in the console and will give you the total score at the end

To be sure that your game's implementation of the GDD is correct, compare the result (render of the run and the total score) of the game run with the `GameRunner`'s run. If there are discrepancies, it means your game's implementation contains incorrectness. In this case, you may ask for clarification in the dedicated AlgoGames 2 discord server.

P.S: the current content of the `actions.txt` in this repo is a run that gets the best score for related `map.txt`. The content of the tests folders are for debugging purposes only, they might not work as intended without modifying the `GameRunner`'s code, but you can take a look at them to see many edge cases that your own implementation should cover.
