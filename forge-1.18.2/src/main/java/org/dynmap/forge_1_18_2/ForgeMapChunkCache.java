package org.dynmap.forge_1_18_2;

import java.util.HashMap;
import java.util.List;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.utils.TileFlags;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since
 * rendering is off server thread
 */
public class ForgeMapChunkCache extends GenericMapChunkCache {
	private ServerLevel w;
	private ServerChunkCache cps;
	/**
	 * Construct empty cache
	 */
	public ForgeMapChunkCache(GenericChunkCache cc) {
		super(cc);
	}

	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
		ChunkAccess ch = cps.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false);
		if (ch != null) {
			CompoundTag nbt = ChunkSerializer.write(w, ch);
			if (nbt != null) {
				gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
			}
		}
		return gc;
	}
	// Load generic chunk from unloaded chunk
	protected GenericChunk loadChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
		CompoundTag nbt = readChunk(chunk.x, chunk.z);
		// If read was good
		if (nbt != null) {
			gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
		}
		return gc;
	}

	public void setChunks(ForgeWorld dw, List<DynmapChunk> chunks) {
		this.w = dw.getWorld();
		if (dw.isLoaded()) {
			/* Check if world's provider is ServerChunkProvider */
			cps = this.w.getChunkSource();
		}
		super.setChunks(dw, chunks);
	}

	private static HashMap<String, TileFlags> tmap = new HashMap<String, TileFlags>();
	
	private CompoundTag readChunk(int x, int z) {
		try {
			CompoundTag rslt = cps.chunkMap.read(new ChunkPos(x, z));
			if (rslt != null) {
				CompoundTag lev = rslt;
				if (lev.contains("Level")) {
					lev = lev.getCompound("Level");
				}
				// Don't load uncooked chunks
				String stat = lev.getString("Status");
				ChunkStatus cs = ChunkStatus.byName(stat);
				if ((stat == null) ||
				// Needs to be at least lighted
						(!cs.isOrAfter(ChunkStatus.LIGHT))) {
					rslt = null;
				}
			}
			if (rslt != null) {
				int version = rslt.getInt("DataVersion");
				if (version < 2975) {
					boolean doIt = false;
					synchronized(tmap) {
						TileFlags tf = tmap.get(dw.getName());
						if (tf == null) {
							tf = new TileFlags();
							tmap.put(dw.getName(), tf);
						}
						if (!tf.getFlag(x, z)) {
							tf.setFlag(x,  z, true);
							doIt = true;
						}
					}
					if (doIt) {
						ChunkPos pos = new ChunkPos(x, z);
						CompoundTag newrec = cps.chunkMap.readChunk(pos);
						if (rslt != null) {
							cps.chunkMap.write(pos, newrec.copy());
						}
					}
				}
			}
			// Log.info(String.format("loadChunk(%d,%d)=%s", x, z, (rslt != null) ?
			// rslt.toString() : "null"));
			return rslt;
		} catch (Exception exc) {
			Log.severe(String.format("Error reading chunk: %s,%d,%d", dw.getName(), x, z), exc);
			return null;
		}
	}
	@Override
	public int getFoliageColor(BiomeMap bm, int[] colormap, int x, int z) {
		return bm.<Biome>getBiomeObject().map(Biome::getSpecialEffects)
				.flatMap(BiomeSpecialEffects::getFoliageColorOverride)
				.orElse(colormap[bm.biomeLookup()]);
	}

	@Override
	public int getGrassColor(BiomeMap bm, int[] colormap, int x, int z) {
		BiomeSpecialEffects effects = bm.<Biome>getBiomeObject().map(Biome::getSpecialEffects).orElse(null);
		if (effects == null) return colormap[bm.biomeLookup()];
		return effects.getGrassColorModifier().modifyColor(x, z, effects.getGrassColorOverride().orElse(colormap[bm.biomeLookup()]));
	}
}
