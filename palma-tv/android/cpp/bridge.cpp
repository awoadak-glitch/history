#include <jni.h>
#include <node.h>
#include <vector>
#include <string>
#include <cstring>
#include <android/log.h>
#include <unistd.h>
#include <pthread.h>
static void* logOutput(void* pointer) {
  int fd=*static_cast<int*>(pointer); delete static_cast<int*>(pointer);
  char buffer[2048]; ssize_t count;
  while((count=read(fd,buffer,sizeof(buffer)-1))>0){buffer[count]=0;__android_log_write(ANDROID_LOG_INFO,"PALMA-Node",buffer);}
  close(fd);return nullptr;
}
extern "C" JNIEXPORT jint JNICALL Java_com_palma_vision_tv_MainActivity_startNode(JNIEnv* env,jclass,jobjectArray arguments) {
  int count=env->GetArrayLength(arguments);std::vector<std::string> values;size_t bytes=0;
  for(int i=0;i<count;i++){auto value=static_cast<jstring>(env->GetObjectArrayElement(arguments,i));const char* text=env->GetStringUTFChars(value,nullptr);values.emplace_back(text);bytes+=values.back().size()+1;env->ReleaseStringUTFChars(value,text);env->DeleteLocalRef(value);}
  std::vector<char> buffer(bytes);std::vector<char*> argv(count+1,nullptr);size_t offset=0;
  for(int i=0;i<count;i++){argv[i]=buffer.data()+offset;std::memcpy(argv[i],values[i].c_str(),values[i].size()+1);offset+=values[i].size()+1;}
  int descriptors[2];if(pipe(descriptors)==0){dup2(descriptors[1],STDOUT_FILENO);dup2(descriptors[1],STDERR_FILENO);close(descriptors[1]);pthread_t thread;pthread_create(&thread,nullptr,logOutput,new int(descriptors[0]));pthread_detach(thread);}
  return node::Start(count,argv.data());
}
