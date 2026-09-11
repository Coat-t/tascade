package com.coatt.tascade;

import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.ModInitializer;

import net.minecraft.util.Identifier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main implements ModInitializer {
	public static final String MOD_ID = "tascade";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
	public enum ProfileState { LOADING, FAILED, SUCCESS };

	public static Integer pp;
	public static Integer elo;
	public static String nickname;
	public static ProfileState profileStatus = ProfileState.LOADING;
	@Override
	public void onInitialize() {
		System.out.println("Loading Tascade");
		TascadeClient.getInstance().connect();
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
