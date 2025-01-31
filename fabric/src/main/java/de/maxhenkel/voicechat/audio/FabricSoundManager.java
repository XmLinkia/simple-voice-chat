package de.maxhenkel.voicechat.audio;

import com.sonicether.soundphysics.SoundPhysics;
import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.VoicechatClient;
import de.maxhenkel.voicechat.voice.client.SoundManager;
import de.maxhenkel.voicechat.voice.client.SpeakerException;
import de.maxhenkel.voicechat.voice.common.NamedThreadPoolFactory;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FabricSoundManager extends SoundManager {

    private boolean soundPhysicsLoaded;
    private boolean soundPhysicsRemasteredLoaded;
    private Method setEnvironment;
    private final ExecutorService executor;

    public FabricSoundManager(@Nullable String deviceName) throws SpeakerException {
        super(deviceName);
        executor = Executors.newSingleThreadExecutor(NamedThreadPoolFactory.create("VoiceChatSoundPhysicsThread"));
        if (!VoicechatClient.CLIENT_CONFIG.soundPhysics.get()) {
            return;
        }
        if (FabricLoader.getInstance().isModLoaded("soundphysics")) {
            try {
                Class.forName("com.sonicether.soundphysics.SoundPhysics");
                initSoundPhysics();
                soundPhysicsLoaded = true;
                Voicechat.LOGGER.warn("Sound Physics can cause very bad performance. Use Sound Physics Remastered instead!");
                Voicechat.LOGGER.info("Successfully initialized Sound Physics");
            } catch (Exception e) {
                Voicechat.LOGGER.warn("Failed to load Sound Physics Remastered: {}", e.getMessage());
                e.printStackTrace();
            }
        }
        if (FabricLoader.getInstance().isModLoaded("sound_physics_remastered")) {
            try {
                Class.forName("com.sonicether.soundphysics.SoundPhysics");
                runInContext(executor, SoundPhysics::init);
                soundPhysicsRemasteredLoaded = true;
                Voicechat.LOGGER.info("Successfully initialized Sound Physics Remastered");
            } catch (Exception e) {
                Voicechat.LOGGER.warn("Failed to load Sound Physics Remastered: {}", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void initSoundPhysics() throws Exception {
        setEnvironment = SoundPhysics.class.getDeclaredMethod("setEnvironment", int.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class);
        setEnvironment.setAccessible(true);
        runInContext(executor, SoundPhysics::init);
    }

    public void resetEnvironment(int source) {
        if (setEnvironment == null) {
            SoundPhysics.setDefaultEnvironment(source);
            return;
        }
        try {
            setEnvironment.invoke(null, source, 0F, 0F, 0F, 0F, 1F, 1F, 1F, 1F, 1F, 1F);
        } catch (Exception e) {
            Voicechat.LOGGER.warn("Failed to execute setEnvironment: {}", e.getMessage());
            e.printStackTrace();
            setEnvironment = null;
        }
    }

    public boolean isSoundPhysicsLoaded() {
        return soundPhysicsLoaded;
    }

    public boolean isSoundPhysicsRemasteredLoaded() {
        return soundPhysicsRemasteredLoaded;
    }

    @Override
    public void close() {
        super.close();
        executor.shutdown();
    }
}
