package de.maxhenkel.voicechat.plugins;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.plugins.impl.*;
import de.maxhenkel.voicechat.plugins.impl.events.*;
import de.maxhenkel.voicechat.plugins.impl.packets.EntitySoundPacketImpl;
import de.maxhenkel.voicechat.plugins.impl.packets.LocationalSoundPacketImpl;
import de.maxhenkel.voicechat.plugins.impl.packets.MicrophonePacketImpl;
import de.maxhenkel.voicechat.plugins.impl.packets.StaticSoundPacketImpl;
import de.maxhenkel.voicechat.voice.common.*;
import de.maxhenkel.voicechat.voice.server.Group;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

public class PluginManager {

    private List<VoicechatPlugin> plugins;
    private Map<Class<? extends Event>, List<Consumer<? extends Event>>> events;

    public void init(Plugin p) {
        Voicechat.LOGGER.info("Loading plugins");
        plugins = loadPlugins(p);
        Voicechat.LOGGER.info("Loaded {} plugin(s)", plugins.size());
        Voicechat.LOGGER.info("Initializing plugins");
        for (VoicechatPlugin plugin : plugins) {
            try {
                plugin.initialize(new VoicechatApiImpl());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Voicechat.LOGGER.info("Initialized {} plugin(s)", plugins.size());
        gatherEvents();
    }

    private void gatherEvents() {
        EventBuilder eventBuilder = EventBuilder.create();
        EventRegistration registration = eventBuilder::addEvent;
        for (VoicechatPlugin plugin : plugins) {
            Voicechat.LOGGER.info("Registering events for {}", plugin.getPluginId());
            try {
                plugin.registerEvents(registration);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        events = eventBuilder.build();
    }

    public <T extends Event> boolean dispatchEvent(Class<T> eventClass, T event) {
        List<Consumer<? extends Event>> events = this.events.get(eventClass);
        if (events == null) {
            return false;
        }
        for (Consumer<? extends Event> evt : events) {
            try {
                Consumer<T> e = (Consumer<T>) evt;
                e.accept(event);
                if (event.isCancelled()) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return event.isCancelled();
    }

    public VoicechatSocket getSocketImplementation(Server server) {
        VoicechatServerStartingEventImpl event = new VoicechatServerStartingEventImpl(new VoicechatServerApiImpl(server));
        dispatchEvent(VoicechatServerStartingEvent.class, event);
        VoicechatSocket socket = event.getSocketImplementation();
        if (socket == null) {
            socket = new VoicechatSocketImpl();
            Voicechat.LOGGER.info("Using default voicechat socket implementation");
        } else {
            Voicechat.LOGGER.info("Using custom voicechat socket implementation: {}", socket.getClass().getName());
        }
        return socket;
    }

    public String getVoiceHost(Player player, String voiceHost) {
        VoiceHostEventImpl event = new VoiceHostEventImpl(new VoicechatServerApiImpl(player.getServer()), voiceHost);
        dispatchEvent(VoiceHostEvent.class, event);
        return event.getVoiceHost();
    }

    public void onServerStarted(Server server) {
        dispatchEvent(VoicechatServerStartedEvent.class, new VoicechatServerStartedEventImpl(new VoicechatServerApiImpl(server)));
    }

    public void onServerStopped(Server server) {
        dispatchEvent(VoicechatServerStoppedEvent.class, new VoicechatServerStoppedEventImpl(new VoicechatServerApiImpl(server)));
    }

    public void onPlayerConnected(@Nullable Player player) {
        if (player == null) {
            return;
        }
        dispatchEvent(PlayerConnectedEvent.class, new PlayerConnectedEventImpl(new VoicechatServerApiImpl(player.getServer()), VoicechatConnectionImpl.fromPlayer(player)));
    }

    public void onPlayerDisconnected(Server server, UUID player) {
        dispatchEvent(PlayerDisconnectedEvent.class, new PlayerDisconnectedEventImpl(new VoicechatServerApiImpl(server), player));
    }

    public boolean onJoinGroup(Player player, @Nullable Group group) {
        if (group == null) {
            return onLeaveGroup(player);
        }
        return dispatchEvent(JoinGroupEvent.class, new JoinGroupEventImpl(new VoicechatServerApiImpl(player.getServer()), new GroupImpl(group), VoicechatConnectionImpl.fromPlayer(player)));
    }

    public boolean onCreateGroup(Player player, Group group) {
        if (group == null) {
            return onLeaveGroup(player);
        }
        return dispatchEvent(CreateGroupEvent.class, new CreateGroupEventImpl(new VoicechatServerApiImpl(player.getServer()), new GroupImpl(group), VoicechatConnectionImpl.fromPlayer(player)));
    }

    public boolean onLeaveGroup(Player player) {
        return dispatchEvent(LeaveGroupEvent.class, new LeaveGroupEventImpl(new VoicechatServerApiImpl(player.getServer()), null, VoicechatConnectionImpl.fromPlayer(player)));
    }

    public boolean onMicPacket(Player player, PlayerState state, MicPacket packet) {
        return dispatchEvent(MicrophonePacketEvent.class, new MicrophonePacketEventImpl(
                new VoicechatServerApiImpl(player.getServer()),
                new MicrophonePacketImpl(packet, player.getUniqueId()),
                new VoicechatConnectionImpl(player, state)
        ));
    }

    public boolean onSoundPacket(@Nullable Player sender, @Nullable PlayerState senderState, Player receiver, PlayerState receiverState, SoundPacket<?> p) {
        VoicechatServerApi api = new VoicechatServerApiImpl(receiver.getServer());
        VoicechatConnection senderConnection = null;
        if (sender != null && senderState != null) {
            senderConnection = new VoicechatConnectionImpl(sender, senderState);
        }

        VoicechatConnection receiverConnection = new VoicechatConnectionImpl(receiver, receiverState);
        if (p instanceof LocationSoundPacket packet) {
            return dispatchEvent(LocationalSoundPacketEvent.class, new LocationalSoundPacketEventImpl(
                    api,
                    new LocationalSoundPacketImpl(packet),
                    senderConnection,
                    receiverConnection
            ));
        } else if (p instanceof PlayerSoundPacket packet) {
            return dispatchEvent(EntitySoundPacketEvent.class, new EntitySoundPacketEventImpl(
                    api,
                    new EntitySoundPacketImpl(packet),
                    senderConnection,
                    receiverConnection
            ));
        } else if (p instanceof GroupSoundPacket packet) {
            return dispatchEvent(StaticSoundPacketEvent.class, new StaticSoundPacketEventImpl(
                    api,
                    new StaticSoundPacketImpl(packet),
                    senderConnection,
                    receiverConnection
            ));
        }
        return false;
    }

    private static PluginManager instance;

    public static PluginManager instance() {
        if (instance == null) {
            instance = new PluginManager();
        }
        return instance;
    }

    public static List<VoicechatPlugin> loadPlugins(Plugin plugin) {
        List<VoicechatPlugin> plugins = new ArrayList<>();
        for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
            if (plugin == p) {
                continue;
            }
            for (Field field : p.getClass().getDeclaredFields()) {
                try {
                    BukkitVoicechatPlugin annotation = field.getDeclaredAnnotation(BukkitVoicechatPlugin.class);
                    if (annotation == null) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object o = field.get(p);
                    if (o instanceof VoicechatPlugin voicechatPlugin) {
                        plugins.add(voicechatPlugin);
                    }
                } catch (Exception e) {
                    Voicechat.LOGGER.warn("Failed to load plugin '{}': {}", field.getName(), e.getMessage());
                }
            }
        }
        return plugins;
    }

}