package com.coatt.tascade;

import com.coatt.tascade.finish.RunFinish;
import com.coatt.tascade.integration.DiscordPresence;
import com.coatt.tascade.integration.RunGoals;
import com.coatt.tascade.integration.RunRecorder;
import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.integration.SpeedrunIGTIntegration;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.coatt.tascade.network.UpdateCheck;
import com.coatt.tascade.replay.record.ReplayRecorder;
import com.coatt.tascade.world.RunWorldOpening;
import com.coatt.tascade.world.WorldSeeds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.Random;

public class Main implements ModInitializer {
	public static final String MOD_ID = "tascade";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
	public enum ProfileState { LOADING, FAILED, SUCCESS };

	public static Integer pp;
	public static Integer elo;
	public static String nickname;
	public static ProfileState profileStatus = ProfileState.LOADING;

	private static boolean speedrunIgtLoaded = false;

	public static void onRaceStart() {
		if (speedrunIgtLoaded) SpeedrunIGTIntegration.onRaceStart();
		ReplayRecorder.raceStarted();
	}

	public static boolean completeRunOnGameWon() {
		if (!speedrunIgtLoaded) return false;
		SpeedrunIGTIntegration.completeOnGameWon();
		return true;
	}

	public static long runRta() {
		long rta = speedrunIgtLoaded ? SpeedrunIGTIntegration.rta() : -1;
		if (rta >= 0) return rta;
		return RunState.raceStartedAtMs > 0 ? RunFinish.elapsedSinceRaceStart() : -1;
	}

	public static void pinRunClock(long raceStartedAtMs) {
		if (speedrunIgtLoaded) SpeedrunIGTIntegration.pinRta(raceStartedAtMs);
	}

	public static long runIgt() {
		long igt = speedrunIgtLoaded ? SpeedrunIGTIntegration.igt() : -1;
		return igt >= 0 ? igt : runRta();
	}

	@Override
	public void onInitialize() {
		System.out.println("Loading Tascade");
		TascadeClient.getInstance().connect();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> TascadeClient.getInstance().onGameClosing()));
		UpdateCheck.start();

		speedrunIgtLoaded = FabricLoader.getInstance().isModLoaded("speedrunigt");
		if (speedrunIgtLoaded) {
			SpeedrunIGTIntegration.init();
		} else {
			LOGGER.warn("SpeedRunIGT is not installed — runs cannot be finished or timed.");
		}
		DiscordPresence.init();
	}

	private static long parseSeed(String seed) {
		if (seed == null || seed.trim().isEmpty()) return new Random().nextLong();
		String trimmed = seed.trim();
		try {
			long parsed = Long.parseLong(trimmed);
			return parsed != 0 ? parsed : trimmed.hashCode();
		} catch (NumberFormatException e) {
			return trimmed.hashCode();
		}
	}

	public static void startTascadeRun(String overworldSeed, String netherSeed,
			JSONObject overworldMeta, JSONObject netherMeta, boolean ranked) {
		startTascadeRun(overworldSeed, netherSeed, overworldMeta, netherMeta, ranked, false);
	}

	public static void startTascadeRun(String overworldSeed, String netherSeed,
			JSONObject overworldMeta, JSONObject netherMeta, boolean ranked, boolean keepProgress) {
		MinecraftClient mcClient = MinecraftClient.getInstance();
		try {
			String worldName = "Tascade " + System.currentTimeMillis() % 1000000L;
			ModifierEffects.newWorld();
			if (keepProgress) {
				RunRecorder.moveToWorld(worldName);
			} else {
				RunRecorder.reset(worldName);
				RunGoals.reset();
			}
			ReplayRecorder.arm(worldName);

			GameRules gameRules = new GameRules();
			// IntRule has no public setter in this mapping, validate() writes the field
			gameRules.get(GameRules.SPAWN_RADIUS).validate("0");

			long overworldSeedLong = parseSeed(overworldSeed);

			// this mirrors GeneratorOptions.getDefaultOptions()
			SimpleRegistry<DimensionOptions> dimensionOptions = DimensionType.method_28517(overworldSeedLong);
			dimensionOptions = GeneratorOptions.method_28608(
					dimensionOptions, GeneratorOptions.createOverworldGenerator(overworldSeedLong));

			// withSeed only reseeds terrain and biomes, ServerWorldMixin handles the rest
			Long netherSeedLong = null;
			if (netherSeed != null && !netherSeed.trim().isEmpty()) {
				netherSeedLong = parseSeed(netherSeed);
				DimensionOptions netherDefault = dimensionOptions.get(DimensionOptions.NETHER);
				DimensionOptions netherOverride = new DimensionOptions(
						netherDefault.getDimensionTypeSupplier(),
						netherDefault.getChunkGenerator().withSeed(netherSeedLong));
				dimensionOptions.set(
						dimensionOptions.getRawId(netherDefault), DimensionOptions.NETHER, netherOverride);
			}
			WorldSeeds.prepare(netherSeedLong, overworldMeta, netherMeta);

			GeneratorOptions generatorOptions =
					new GeneratorOptions(overworldSeedLong, true, false, dimensionOptions);

			LevelInfo levelInfo = new LevelInfo(
					worldName,
					GameMode.SURVIVAL,
					false, // real hardcore flag (this mapping build swaps LevelInfo's hardcore/structures getters)
					Difficulty.EASY,
					RunState.cheats,
					gameRules,
					DataPackSettings.SAFE_MODE);

			RegistryTracker.Modifiable registryTracker =
					DimensionType.addRegistryDefaults(new RegistryTracker.Modifiable());

			mcClient.method_29970(new SaveLevelScreen(new TranslatableText("createWorld.preparing")));
			RunWorldOpening.run(() -> mcClient.method_29607(worldName, levelInfo, registryTracker, generatorOptions));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
