import app.quanneng.MediaCandidatePolicy;
public final class MediaCandidateCheck {
    public static void main(String[] args){
        expect(MediaCandidatePolicy.playable("https://cdn.example/master.m3u8?token=1"));
        expect(MediaCandidatePolicy.playable("https://cdn.example/stream.mpd"));
        expect(!MediaCandidatePolicy.playable("https://cdn.example/chunk-0001.mp4"));
        expect(!MediaCandidatePolicy.playable("https://cdn.example/init.mp4"));
        expect(!MediaCandidatePolicy.playable("https://cdn.example/42.m4s"));
        expect(!MediaCandidatePolicy.playable("https://cdn.example/42.mp4"));
        expect(MediaCandidatePolicy.playable("https://cdn.example/Solaris.1972.1080p.mp4"));
        expect(MediaCandidatePolicy.priority("https://cdn.example/master.m3u8","")<MediaCandidatePolicy.priority("https://cdn.example/clip.mp4",""));
        expect(MediaCandidatePolicy.mime("https://cdn.example/stream","application/dash+xml; charset=UTF-8").equals("application/dash+xml"));
        expect(MediaCandidatePolicy.exportable("https://cdn.example/123.mp4",true));
        expect(!MediaCandidatePolicy.exportable("https://cdn.example/123.mp4",false));
        expect(!MediaCandidatePolicy.exportable("https://cdn.example/chunk-123.mp4",true));
        expect(!MediaCandidatePolicy.exportable("https://cdn.example/init.mp4",true));
        expect(!MediaCandidatePolicy.exportable("https://cdn.example/1.m4s",true));
        System.out.println("Media candidate policy passed");
    }
    private static void expect(boolean good){if(!good)throw new AssertionError("Media candidate classification failed");}
}
