package me.internalizable.numdrassl;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.plugin.early.ClassTransformer;
import javassist.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Level;

public class BridgePacketsTransformer implements ClassTransformer {

    private static final String PACKET_REGISTRY = "com.hypixel.hytale.protocol.PacketRegistry";

    private final ClassPool classPool;

    public BridgePacketsTransformer() {
        this.classPool = ClassPool.getDefault();
    }

    @Nullable
    @Override
    public byte[] transform(@Nonnull String name, @Nonnull String path, @Nonnull byte[] bytes) {
        try {
            if (PACKET_REGISTRY.equals(name)) {
                HytaleLogger.get("BridgePackets|EP").at(Level.INFO).log("Found PacketRegistry, patching...");
                return patchPacketRegistry(bytes);
            }
        } catch (NullPointerException | IOException | NotFoundException | CannotCompileException e) {
            HytaleLogger.get("BridgePackets|EP").at(Level.WARNING).log("ERROR patching " + name + ": " + e.getMessage());
            e.printStackTrace();
        }

        return bytes;
    }

    private byte[] patchPacketRegistry(byte[] bytes) throws IOException, NotFoundException, CannotCompileException {
        HytaleLogger.get("BridgePackets|EP").at(Level.INFO).log("Patching PacketRegistry...");

        CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(bytes));

        String methodCode =
                "public static void registerCustomPacket(" +
                        "int id, " +
                        "String name, " +
                        "Class type, " +
                        "int fixedBlockSize, " +
                        "int maxSize, " +
                        "boolean compressed, " +
                        "java.util.function.BiFunction validate, " +
                        "java.util.function.BiFunction deserialize" +
                        ") {" +
                        // Calls the existing private register(...) method inside the same class
                        "register(id, name, type, fixedBlockSize, maxSize, compressed, validate, deserialize);" +
                        "}";

        CtMethod newMethod = CtNewMethod.make(methodCode, ctClass);

        ctClass.addMethod(newMethod);

        byte[] result = ctClass.toBytecode();
        ctClass.detach();

        HytaleLogger.get("BridgePackets|EP").at(Level.INFO).log("PacketRegistry patched successfully");
        return result;
    }

    @Override
    public int priority() {
        return 100;
    }
}
