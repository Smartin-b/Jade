package snownee.jade.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import snownee.jade.Jade;

public final class UsernameCache {

	private static Map<UUID, String> map = new HashMap<>();
	private static Set<UUID> downloadingList = new HashSet<>();

	private static final Path saveFile = PlatformProxy.getConfigDirectory().toPath().resolve(Jade.MODID + "/usernamecache.json");
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	private UsernameCache() {
	}

	/**
	 * Set a player's current usernamee
	 *
	 * @param uuid
	 *            the player's {@link java.util.UUID UUID}
	 * @param username
	 *            the player's username
	 */
	public static void setUsername(UUID uuid, String username) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(username);

		if (username.equals(map.get(uuid)))
			return;

		map.put(uuid, username);
		save();
	}

	/**
     * Remove a player's username from the cache
     *
     * @param uuid
     *            the player's {@link java.util.UUID UUID}
     * @return if the cache contained the user
     */
	public static boolean removeUsername(UUID uuid) {
		Objects.requireNonNull(uuid);

		if (map.remove(uuid) != null) {
			save();
			return true;
		}

		return false;
	}

	/**
	 * Get the player's last known username
	 * <p>
	 * <b>May be <code>null</code></b>
	 *
	 * @param uuid
	 *            the player's {@link java.util.UUID UUID}
	 * @return the player's last known username, or <code>null</code> if the
	 *         cache doesn't have a record of the last username
	 */
	@Nullable
	public static String getLastKnownUsername(UUID uuid) {
		Objects.requireNonNull(uuid);
		String name = map.get(uuid);
		if(name==null){
			download(uuid);
		}
		return name;
	}

	/**
     * Check if the cache contains the given player's username
     *
     * @param uuid
     *            the player's {@link java.util.UUID UUID}
     * @return if the cache contains a username for the given player
     */
	public static boolean containsUUID(UUID uuid) {
		Objects.requireNonNull(uuid);
		return map.containsKey(uuid);
	}

	/**
	 * Get an immutable copy of the cache's underlying map
	 *
	 * @return the map
	 */
	public static Map<UUID, String> getMap() {
		return ImmutableMap.copyOf(map);
	}

	/**
	 * Save the cache to file
	 */
	public static void save() {
		new SaveThread(gson.toJson(map)).start();
	}

	/**
	 * Load the cache from file
	 */
	public static void load() {
		if (!Files.exists(saveFile))
			return;

		try (final BufferedReader reader = Files.newBufferedReader(saveFile, Charsets.UTF_8)) {
			@SuppressWarnings("serial")
			Type type = new TypeToken<Map<UUID, String>>() {
			}.getType();
			map = gson.fromJson(reader, type);
		} catch (JsonSyntaxException | IOException e) {
			Jade.LOGGER.error("Could not parse username cache file as valid json, deleting file {}", saveFile, e);
			WailaExceptionHandler.handleErr(e, null, null);
			try {
				Files.delete(saveFile);
			} catch (IOException e1) {
				Jade.LOGGER.error("Could not delete file {}", saveFile.toString());
			}
		} finally {
			// Can sometimes occur when the json file is malformed
			if (map == null) {
				map = new HashMap<>();
			}
		}
	}

	/**
	 * Downloads a Username
	 * This function can be called repeatedly
	 * It should only attempt one Download
	 */
	private static void download(UUID uuid) {
		if(downloadingList.contains(uuid)){
			return;
		}
		downloadingList.add(uuid);
		Jade.LOGGER.warn("Starting Donwload "+uuid);
		new DownloadThread(uuid).start();
	}

	/**
	 * Downloads GameProfile by UUID then saves them to disk
	 * representation of the cache to disk
	 */
	private static class DownloadThread extends Thread {
		private final UUID uuid;

		public DownloadThread(UUID uuid) {
			this.uuid = uuid;
		}

		@Override
		public void run() {
			try {
				//if the downloading fails for some reason and throws an error,
				GameProfile profile = new GameProfile(uuid,"???");
				profile = Minecraft.getInstance().getMinecraftSessionService().fillProfileProperties(profile,true);
				if(!(profile.getName()==null||profile.getName().equals("???"))) {
					//only remove from list if it was successfull
					//if it failed for some reason leave it in the channel so no repeated tries are made
					UsernameCache.setUsername(profile.getId(),profile.getName());
					downloadingList.remove(uuid);
				}
			} catch (Exception e) {
				Jade.LOGGER.error("Download for uuid "+uuid+ " failed");
			}
		}
	}

	/**
     * Used for saving the {@link com.google.gson.Gson#toJson(Object) Gson}
     * representation of the cache to disk
     */
	private static class SaveThread extends Thread {

		/** The data that will be saved to disk */
		private final String data;

		public SaveThread(String data) {
			this.data = data;
		}

		@Override
		public void run() {
			try {
				// Make sure we don't save when another thread is still saving
				synchronized (saveFile) {
					Files.write(saveFile, data.getBytes(StandardCharsets.UTF_8));
				}
			} catch (IOException e) {
				Jade.LOGGER.error("Failed to save username cache to file!");
			}
		}
	}
}
