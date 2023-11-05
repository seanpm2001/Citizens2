package net.citizensnpcs.nms.v1_19_R3.util;

import java.lang.invoke.MethodHandle;
import java.util.Set;

import org.bukkit.Bukkit;

import com.google.common.collect.ForwardingSet;

import net.citizensnpcs.api.event.NPCLinkToPlayerEvent;
import net.citizensnpcs.api.event.NPCSeenByPlayerEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.nms.v1_19_R3.entity.EntityHumanNPC;
import net.citizensnpcs.npc.ai.NPCHolder;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.Util;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkMap.TrackedEntity;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;

public class CitizensEntityTracker extends ChunkMap.TrackedEntity {
    private final Entity tracker;

    public CitizensEntityTracker(ChunkMap map, Entity entity, int i, int j, boolean flag) {
        map.super(entity, i, j, flag);
        this.tracker = entity;
        try {
            Set<ServerPlayerConnection> set = (Set<ServerPlayerConnection>) TRACKING_SET_GETTER.invoke(this);
            TRACKING_SET_SETTER.invoke(this, new ForwardingSet<ServerPlayerConnection>() {
                @Override
                public boolean add(ServerPlayerConnection conn) {
                    boolean res = super.add(conn);
                    if (res) {
                        Bukkit.getPluginManager().callEvent(new NPCLinkToPlayerEvent(((NPCHolder) tracker).getNPC(),
                                conn.getPlayer().getBukkitEntity()));
                    }

                    return res;
                }

                @Override
                protected Set<ServerPlayerConnection> delegate() {
                    return set;
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    public CitizensEntityTracker(ChunkMap map, TrackedEntity entry) {
        this(map, getTracker(entry), getTrackingDistance(entry), getE(entry), getF(entry));
    }

    @Override
    public void updatePlayer(final ServerPlayer entityplayer) {
        if (entityplayer instanceof EntityHumanNPC)
            return;

        if (!seenBy.contains(entityplayer.connection) && tracker instanceof NPCHolder) {
            NPC npc = ((NPCHolder) tracker).getNPC();
            if (REQUIRES_SYNC == null) {
                REQUIRES_SYNC = !Bukkit.isPrimaryThread();
            }

            boolean cancelled = Util.callPossiblySync(() -> {
                NPCSeenByPlayerEvent event = new NPCSeenByPlayerEvent(npc, entityplayer.getBukkitEntity());
                try {
                    Bukkit.getPluginManager().callEvent(event);
                } catch (IllegalStateException e) {
                    REQUIRES_SYNC = true;
                    throw e;
                }

                if (event.isCancelled())
                    return true;
                Integer trackingRange = npc.data().<Integer> get(NPC.Metadata.TRACKING_RANGE);
                if (TRACKING_RANGE_SETTER != null && trackingRange != null
                        && npc.data().get("last-tracking-range", -1) != trackingRange.intValue()) {
                    try {
                        TRACKING_RANGE_SETTER.invoke(CitizensEntityTracker.this, trackingRange);
                        npc.data().set("last-tracking-range", trackingRange);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }

                }

                return false;
            }, REQUIRES_SYNC);

            if (cancelled)
                return;
        }

        super.updatePlayer(entityplayer);
    }

    private static int getE(TrackedEntity entry) {
        try {
            return (int) E.invoke(TRACKER_ENTRY.invoke(entry));
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return 0;
    }

    private static boolean getF(TrackedEntity entry) {
        try {
            return (boolean) F.invoke(TRACKER_ENTRY.invoke(entry));
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return false;
    }

    private static Entity getTracker(TrackedEntity entry) {
        try {
            return (Entity) TRACKER.invoke(entry);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private static int getTrackingDistance(TrackedEntity entry) {
        try {
            return (Integer) TRACKING_RANGE.invoke(entry);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return 0;
    }

    private static final MethodHandle E = NMS.getGetter(ServerEntity.class, "e");
    private static final MethodHandle F = NMS.getGetter(ServerEntity.class, "f");
    private static volatile Boolean REQUIRES_SYNC = false;
    private static final MethodHandle TRACKER = NMS.getFirstGetter(TrackedEntity.class, Entity.class);
    private static final MethodHandle TRACKER_ENTRY = NMS.getFirstGetter(TrackedEntity.class, ServerEntity.class);
    private static final MethodHandle TRACKING_RANGE = NMS.getFirstGetter(TrackedEntity.class, int.class);
    private static final MethodHandle TRACKING_RANGE_SETTER = NMS.getFirstFinalSetter(TrackedEntity.class, int.class);
    private static final MethodHandle TRACKING_SET_GETTER = NMS.getFirstGetter(TrackedEntity.class, Set.class);
    private static final MethodHandle TRACKING_SET_SETTER = NMS.getFirstFinalSetter(TrackedEntity.class, Set.class);
}
