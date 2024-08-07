#include <jni.h>
#include <stdlib.h>

static void* allocatedMemory = NULL;

extern "C" JNIEXPORT void JNICALL
Java_com_example_envapp_ResourceSimulator_allocateMemory(JNIEnv *env, jobject thiz, jint intensity) {
// Free previously allocated memory if any
if (allocatedMemory != NULL) {
free(allocatedMemory);
allocatedMemory = NULL;
}

// Calculate memory to allocate (max 25% of 2GB, scaled by intensity)
long maxMemory = 536870912; // 512MB (25% of 2GB)
long memoryToAllocate = (intensity * maxMemory) / 100;

// Allocate memory
allocatedMemory = malloc(memoryToAllocate);

// If allocation successful, write some data to ensure it's not optimized out
if (allocatedMemory != NULL) {
char* buffer = (char*)allocatedMemory;
for (long i = 0; i < memoryToAllocate; i += 4096) { // Write every 4KB
buffer[i] = 1;
}
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_envapp_ResourceSimulator_freeAllocatedMemory(JNIEnv *env, jobject thiz) {
if (allocatedMemory != NULL) {
free(allocatedMemory);
allocatedMemory = NULL;
}
}