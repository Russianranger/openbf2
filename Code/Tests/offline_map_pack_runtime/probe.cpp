// [TEMP] Focused VFS/metadata fixture; uses real file, ZIP, XML and registry implementations.
#include "IO/File/FileManager.h"
#include "IO/File/UnixFileSystem.h"
#include "IO/File/ZipArchive.h"
#include "IO/File/OfflineMapPack.h"
#include "IO/Stream/MemStream.h"
#include "Core/ClassManager.h"
#include "Core/SmartItf.h"
#include "Core/SettingsRepostitory.h"
#include "Core/Ids.h"
#include "Game/ModInfo.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <limits.h>
#include <unistd.h>
using namespace dice::hfe;
namespace {
const char* packId="openbf2_maps_0123456789abcdef0123456789abcdef";
int checks=0;
void check(bool ok,const char* text) { ++checks; if(!ok){std::fprintf(stderr,"FAIL %s\n",text);std::exit(1);} }
struct MemoryModule : IClassServer {
 void addRef() override {} int getRef() override{return 1;} int release() override{return 1;}
 void* queryInterface(unsigned) const override{return nullptr;}
 void getClassList(std::vector<ClassRegInfo>& out) override {out.push_back({io::CID_MemStream,"MemStream",io::MemStream::create});}
 void getSingletonList(std::vector<SingletonRegInfo>&) override {}
};
void select(const char* level) {
 setenv("BF2_PORT_MAP_PACK",packId,1); setenv("BF2_PORT_LOCAL_COOP","1",1);
 setenv("BF2_PORT_DIRECT_START","1",1);setenv("BF2_PORT_HOST_LEVEL",level,1);
 setenv("BF2_PORT_HOST_MODE","gpm_coop",1);setenv("BF2_PORT_HOST_SIZE","64",1);
}
std::string read(io::FileManager& files,const std::string& path) {
 char buffer[8192]={}; unsigned count=0;
 if(!files.readFile(path,buffer,sizeof(buffer),count))return {};
 return std::string(buffer,count);
}
std::string streamRead(io::FileManager& files,const std::string& path) {
 io::IStream* stream=files.open(path,io::FILEMODE_READ,static_cast<io::FileAccess>(0),0);
 if(!stream)return {};char b[8192];unsigned n=stream->read(b,sizeof(b));stream->release();return {b,n};
}
void metadata(const char* level,int size,const char* title,bool onlyBase=true) {
 ModInfo info;check(info.init(),"real ModInfo discovery succeeds");
 auto* mods=info.getModList();check(mods&&(!onlyBase||mods->size()==1),"pack never becomes a mod");
 const auto* base=info.getCurrentMod();check(base&&base->path=="mods/bf2","GSModDirectory remains base mod");
 const auto* map=info.getMap(level);check(map&&map->name==title,"selected descriptor supplies map identity");
 check(map->gameModes.size()==1&&map->gameModes[0].mapTypes.size()==1&&
       map->gameModes[0].mapTypes[0].players==size,"native menu sees only selected descriptor sizes");
 int duplicates=0;for(const auto& row:base->maps)if(host_layout::equalAsciiNoCase(row.path.c_str(),level))++duplicates;
 check(duplicates==1,"duplicate stock map is replaced exactly once");
}
}
int main(int argc,char**argv) {
 if((argc!=2&&argc!=3)||chdir(argv[1])!=0)return 2;
 if(argc==3) {
  Dl_info symbol{};char loaded[PATH_MAX],expected[PATH_MAX];
  check(dladdr(dlsym(RTLD_DEFAULT,"_ZN4dice3hfe7ModInfo4initEv"),&symbol)&&symbol.dli_fname&&
        realpath(symbol.dli_fname,loaded)&&realpath(argv[2],expected)&&std::strcmp(loaded,expected)==0,
        "production metadata function resolves to exact supplied library");
  std::printf("Exact production library: %s\n",loaded);
 }
 setenv("BF2_ANDROID_GAME_ROOT",argv[1],1);
 ClassManager registry; MemoryModule memory; registry.registerServer(&memory); classManager=&registry;
 SettingsRepostitory repository; settings=&repository; repository.stringSet("GSModDirectory","mods/bf2");
 io::FileManager files;io::fileManager=&files;auto* disk=new io::UnixFileSystem;files.setBaseSystem(disk);disk->release();
 auto* zip=new io::ZipArchiveHandler;files.addArchiveHandler(zip);zip->release();files.addPath("mods/bf2");
 select("StockMap");offline_maps::Selection selected;
 check(offline_maps::selection(selected)&&offline_maps::requiredFilesPresent(selected),"valid pack selection and required files");
 std::string write;files.getWritePath("Settings/career.ini",write);
 check(write=="mods/bf2/Settings/career.ini","ordinary write path remains base");
 check(files.writeFile("Settings/career.ini","save",4,false),"ordinary settings save succeeds");
 check(read(files,"Settings/career.ini")=="save","ordinary settings save is readable");
 check(!files.writeFile("levels/stockmap/info/new.txt","bad",3,false),"virtual selected-level writes cannot modify pack or stock map");
 check(!files.deleteFile(selected.levelDirectory()+"server.zip"),"physical selected pack path is read-only");
 check(!files.open("levels/stockmap/server.zip",static_cast<io::FileMode>(1),io::FILEACCESS_2,0),"write stream cannot truncate stock counterpart");
 check(!files.moveFile("levels/stockmap/info/stockmap.desc","Settings/moved.desc")&&
       !files.copyFile("Settings/career.ini","levels/stockmap/info/copy.txt",false),"move/copy cannot mutate selected map");
 check(read(files,"Common/shared.txt")=="BASE ART","base shared assets win over unrelated pack files");
 check(read(files,"Python/private.txt")=="BASE PYTHON","pack does not override base Python");
 const auto desc=read(files,"LEVELS\\STOCKMAP\\INFO\\STOCKMAP.DESC");
 check(desc.find("Pack StockMap")!=std::string::npos,"mixed-case Info reads resolve selected pack");
 check(streamRead(files,"@ROOT@/mods/bf2/Levels/stockmap/Info/stockmap.desc")==desc,"root-qualified descriptor resolves pack");
 check(read(files,std::string(argv[1])+"/mods/bf2/levels/stockmap/info/stockmap.desc")==desc,"absolute descriptor resolves pack");
 metadata("stockmap",64,"Pack StockMap");
 check(files.fileExists("Levels/stockmap_extra/Info/stockmap_extra.desc"),"map prefix does not capture another map");
 files.permitMountArchives(true);
 for(int round=0;round<3;++round) {
  check(files.mountArchive("LEVELS/StockMap/SERVER.ZIP","levels/stockmap",true)!=nullptr,"selected server archive mounts");
  check(files.mountArchive("levels/stockmap/client.zip","levels/stockmap",true)!=nullptr,"selected client archive mounts");
  const std::string gameplay="levels/stockmap/GameModes/gpm_coop/64/GamePlayObjects.con";
  check(read(files,gameplay)=="PACK64","archive lookup prefers selected data over same-name base map");
  check(streamRead(files,gameplay)=="PACK64","actual ZIP to MemStream open reads selected data");
  check(read(files,"@ROOT@/mods/bf2/"+gameplay)=="PACK64","root-qualified archive reads remain in selected map");
  check(read(files,"levels/stockmap/client.marker")=="PACK CLIENT","client archive content is selected");
  const std::string missing="levels/stockmap/GameModes/gpm_coop/16/GamePlayObjects.con";
  check(!files.fileExists(missing)&&files.fileExistsEx2(missing).empty(),"missing selected layout cannot fall back to base");
  check(read(files,missing).empty()&&streamRead(files,missing).empty(),"both read APIs refuse base layout fallback");
  io::FileInfo fi;check(!files.getFileInfo(missing,fi),"metadata lookup refuses missing layout fallback");
  check(!files.openwin32(missing,io::FILEMODE_READ,static_cast<io::FileAccess>(0),0),"loose-only read refuses base level fallback");
  std::list<io::FileInfo> rows;files.findFiles("levels/stockmap/GameModes/gpm_coop",true,rows);
  bool old=false;for(const auto& row:rows)if(row.name.find("16/")==0)old=true;
  check(!old,"directory enumeration cannot discover old stock layout");
  check(files.unmountArchive("levels/stockmap/server.zip"),"exact virtual server unmount finds pack filename");
  check(files.unmountArchive("levels/stockmap/client.zip"),"exact virtual client unmount finds pack filename");
  std::vector<io::IArchive*> mounted;files.getAllArchives(mounted);check(mounted.empty(),"round teardown releases all pack mounts");
 }
 const std::string archive=selected.levelDirectory()+"server.zip";
 check(std::rename(archive.c_str(),(archive+".off").c_str())==0,"fixture removes selected archive");
 check(!offline_maps::requiredFilesPresent(selected)&&!files.mountArchive("levels/stockmap/server.zip","levels/stockmap",true),"missing pack archive never mounts stock counterpart");
 check(std::rename((archive+".off").c_str(),archive.c_str())==0,"fixture restores selected archive");
 const std::string descriptor=selected.levelDirectory()+"info/stockmap.desc";
 check(std::rename(descriptor.c_str(),(descriptor+".off").c_str())==0,"fixture removes descriptor");
 check(!files.fileExists("levels/stockmap/info/stockmap.desc")&&read(files,"levels/stockmap/info/stockmap.desc").empty(),"missing descriptor cannot fall back to base metadata");
 {ModInfo missing;check(!missing.init(),"native metadata discovery fails closed on missing pack descriptor");}
 check(std::rename((descriptor+".off").c_str(),descriptor.c_str())==0,"fixture restores descriptor");
 select("newmap");metadata("newmap",64,"Pack NewMap");
 {ModInfo info;check(info.init()&&!info.getMap("otherpackmap"),"only selected pack map enters base catalogue");}
 unsetenv("BF2_PORT_MAP_PACK");metadata("stockmap",16,"Base StockMap");
 check(read(files,"levels/stockmap/GameModes/gpm_coop/16/GamePlayObjects.con")=="BASE16","no-pack launch restores ordinary base reads");
 check(read(files,"Common/shared.txt")=="BASE ART","no-pack shared content remains unchanged");
 select("stockmap");setenv("BF2_PORT_LOCAL_COOP","0",1);
 check(!offline_maps::selection(selected),"remote/non-offline context cannot activate overlay");
 check(!offline_maps::validId("../bf2")&&!offline_maps::validId("openbf2_maps_0123456789ABCDEF0123456789abcdef"),"pack ID rejects paths and uppercase UUID");
 check(!offline_maps::validLevel("../stockmap")&&!offline_maps::validLevel("stockmap;quit"),"map selection rejects command/path separators");
 std::printf("PASS %d actual Android VFS/ZIP/XML checks\n",checks);
 // Registry/global shutdown belongs to the full engine; this focused process owns them to exit.
 std::fflush(stdout);std::_Exit(0);
}
