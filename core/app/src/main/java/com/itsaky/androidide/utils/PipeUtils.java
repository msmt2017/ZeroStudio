package com.itsaky.androidide.utils;

import androidx.annotation.NonNull;
import java.io.FileDescriptor;
import java.io.PipedInputStream;
import java.lang.reflect.Field;

public final class PipeUtils {
    private PipeUtils() {}

    public static int getFd(@NonNull FileDescriptor fileDescriptor) {
        try {
            Field fdField = FileDescriptor.class.getDeclaredField("descriptor");
            fdField.setAccessible(true);
            return (int) fdField.get(fileDescriptor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get file descriptor via reflection", e);
        }
    }

    public static int getPipedInputStreamFd(@NonNull PipedInputStream pipedInputStream) {
        try {
            //We need the native fd of the read-end of the pipe.
            // PipedInputStream itself doesn't expose it directly, but its internal 'sink'
            // (a PipedOutputStream) is connected to a 'source' (another PipedInputStream)
            // which does have a FileDescriptor.
            Field sinkField = PipedInputStream.class.getDeclaredField("sink");
            sinkField.setAccessible(true);
            Object sink = sinkField.get(pipedInputStream);

            Field sourceField = sink.getClass().getDeclaredField("source");
            sourceField.setAccessible(true);
            Object source = sourceField.get(sink);

            Field fdField = PipedInputStream.class.getDeclaredField("fd");
            fdField.setAccessible(true);
            FileDescriptor fileDescriptor = (FileDescriptor) fdField.get(source);

            return getFd(fileDescriptor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get PipedInputStream file descriptor", e);
        }
    }
}