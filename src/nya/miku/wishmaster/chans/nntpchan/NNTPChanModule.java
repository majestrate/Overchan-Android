package nya.miku.wishmaster.chans.nntpchan;


import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.util.Pools;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import nya.miku.wishmaster.lib.org_json.JSONTokener;

/**
 * @author jeff
 * nntpchan (srndv2) overchan module
 */
public class NNTPChanModule extends AbstractChanModule {

    private final String _baseUrl;

    private static final String SERVER_2HU = "https://2hu-ch.org";
    private static final String DEFAULT_FRONTEND_NAME = "2hu-ch.org";

    private static final String DEFAULT_BOARD_NAME = "overchan.test";
    private static final String DEFAULT_BOARD_CATEGORY = "nntpchan";

    private static final String TAG = "nntpchan";

    private static final Pattern RE_BOARDNAME = Pattern.compile("(overchan\\.[\\w\\.]+\\w)\\-(\\d)");
    private static final Pattern RE_THREADNAME = Pattern.compile("thread\\-([\\da-fA-F]{40})");

    public NNTPChanModule(SharedPreferences prefs, Resources res) {
        super(prefs, res);
        _baseUrl = SERVER_2HU;
        Log.i(TAG, "nntpchan loaded yeeeh");
    }

    private String _makeURL(String path) {
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        String ret = String.format("%s/%s", _baseUrl, path);
        Log.i(TAG, String.format("made url : %s", ret));
        return ret;
    }

    private String _thumbURL(String path) {
        return String.format("%s/thm/%s.jpg",_baseUrl, path);
    }

    private String _imgURL(String path) {
        return String.format("%s/img/%s", _baseUrl, path);
    }

    private String _threadURL(String msgid) {
        String posthash = CryptoUtils.computeSHA1(msgid);
        return _makeURL(String.format("thread-%s.json", posthash));
    }

    private String _boardURL(String board, int page) {
        if(page < 0) {
            page = 0;
        }
        return _makeURL(String.format("%s-%d.json", board, page));

    }

    private String _makeBoardDesc(String board) {
        return String.format("%s on %s", board, getChanName());
    }

    private PostModel postFromJSONObject(JSONObject obj) {
        PostModel post = new PostModel();
        post.number = obj.getString("HashLong");
        post.comment = obj.getString("PostMessage");
        post.parentThread = obj.getString("Parent");
        post.name = obj.getString("PostName");
        post.op = obj.getString("Message_id").equals(obj.getString("Parent"));
        post.subject = obj.getString("PostSubject");
        if (obj.isNull("Files")) {
            post.attachments = new AttachmentModel[0];
        } else {
            JSONArray files = obj.getJSONArray("Files");
            int flen = files.length();
            if (flen > 0 ) {
                post.attachments = new AttachmentModel[flen];
                for(int idx = 0; idx < flen ; idx ++) {
                    JSONObject jatt = files.getJSONObject(idx);
                    AttachmentModel att = new AttachmentModel();
                    String attpath = jatt.getString("Path");
                    att.thumbnail = _thumbURL(attpath);
                    att.originalName = jatt.getString("Name");
                    att.path = _imgURL(attpath);
                    att.size = -1;
                    att.width = -1;
                    att.height = -1;
                    attpath = attpath.toLowerCase();
                    if(attpath.endsWith(".gif")) {
                        att.type = AttachmentModel.TYPE_IMAGE_GIF;
                    } else if (attpath.endsWith(".jpg") || attpath.endsWith(".jpeg") || attpath.endsWith(".png")) {
                        att.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    } else if (attpath.endsWith(".mp4") || attpath.endsWith(".webm") || attpath.endsWith(".mkv")) {
                        att.type = AttachmentModel.TYPE_VIDEO;
                    } else {
                        att.type = AttachmentModel.TYPE_OTHER_FILE;
                    }
                    post.attachments[idx] = att;
                }
            }
        }
        return post;
    }

    public CaptchaModel getNewCaptcha(String boardname, String threadno, ProgressListener listener, CancellableTask cancel) throws Exception {
        return downloadCaptcha(_makeURL("/captcha/img"), listener, cancel);
    }

    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception  {
        String refmsgid = model.threadNumber;

        String url = _makeURL(String.format("/post/%s?json", model.boardName));

        ExtendedMultipartBuilder postBuilder = ExtendedMultipartBuilder.create().
                setCharset(Charset.forName("UTF-8")).setDelegates(listener,task);
        postBuilder.addString("captcha", model.captchaAnswer);
        postBuilder.addString("message", model.comment);
        postBuilder.addString("subject", model.subject);
        postBuilder.addString("name", model.name);
        postBuilder.addString("reference", refmsgid);

        HttpRequestModel request = HttpRequestModel.builder().setPOST(postBuilder.build()).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, listener, task);
            BufferedReader in = new BufferedReader(new InputStreamReader(response.stream));
            JSONObject jresult = new JSONObject(new JSONTokener(in));

            if (jresult.isNull("error")) {
                 return jresult.getString("url");
            }
            throw new Exception(jresult.getString("error"));

        } finally {
            if(response != null) response.release();
        }
    }


    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {

        JSONArray j = downloadJSONArray(_makeURL("/boards.json"), false, listener, task);

        if(j == null) return oldBoardsList;

        int len = j.length();

        if (len == 0) return new SimpleBoardModel[0];

        SimpleBoardModel[] boards = new SimpleBoardModel[len];
        for(int idx = 0; idx < len; idx++) {
            SimpleBoardModel m = new SimpleBoardModel();
            m.chan = getChanName();
            m.nsfw = true;
            m.boardName = j.getString(idx);
            m.boardDescription = _makeBoardDesc(m.boardName);
            m.boardCategory = DEFAULT_BOARD_CATEGORY;
            boards[idx] = m;
        }
        return boards;
    }

    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList) throws Exception {
        JSONArray j = downloadJSONArray(_boardURL(boardName, page), false, listener, task);

        if(j == null) return oldList;

        int len = j.length();

        if (len == 0) return new ThreadModel[0];

        ThreadModel[] threads = new ThreadModel[len];
        for (int idx = 0; idx < len; idx ++) {
            JSONArray jthread = j.getJSONArray(idx);
            int postcount = jthread.length();
            ThreadModel model = new ThreadModel();
            model.posts = new PostModel[postcount];
            for (int postidx = 0; postidx < postcount; postidx++) {
                JSONObject jpost = jthread.getJSONObject(postidx);
                model.posts[postidx] = postFromJSONObject(jpost);
            }
            model.threadNumber = model.posts[0].number;
            threads[idx] = model;
        }
        return threads;
    }

    public String getChanName() {
        return DEFAULT_FRONTEND_NAME;
    }

    public String getDisplayingName() {
        return getChanName();
    }

    public UrlPageModel parseUrl(String url) {
        Log.i(TAG, url);
        if (UrlPathUtils.getUrlPath(url, DEFAULT_FRONTEND_NAME) == null) throw new IllegalArgumentException("wrong domain");
        Log.i(TAG, "good domain");
        UrlPageModel model = new UrlPageModel();
        model.chanName = getChanName();
        Matcher m = RE_THREADNAME.matcher(url);
        if(m.find()) {
            model.postNumber = m.group(1);
            model.type = UrlPageModel.TYPE_THREADPAGE;
            return model;
        }
        m = RE_BOARDNAME.matcher(url);
        if(m.find()) {
            model.boardName = m.group(1);
            model.boardPage = Integer.parseInt(m.group(2));
            model.type = UrlPageModel.TYPE_BOARDPAGE;
            return model;
        }
        Log.i(TAG, "Bad url");
        throw new IllegalArgumentException("bad url");
    }

    public String buildUrl(UrlPageModel model) {
        if(!model.chanName.equals(getChanName())) throw new IllegalArgumentException("wrong chan");
        switch(model.type) {
            case UrlPageModel.TYPE_BOARDPAGE:
                return _boardURL(model.boardName, model.boardPage);
            case UrlPageModel.TYPE_THREADPAGE:
                return _threadURL(model.threadNumber);
            default:
                return SERVER_2HU;

        }
    }

    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_nntpchan, null);
    }

    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        JSONArray jposts = downloadJSONArray(_threadURL(threadNumber), false, listener, task);

        if (jposts == null) return oldList;

        int len = jposts.length();
        if (len == 0) return new PostModel[0];

        PostModel[] posts = new PostModel[len];

        for (int idx = 0; idx < len; idx ++) {
            posts[idx] = postFromJSONObject(jposts.getJSONObject(idx));
        }

        return posts;
    }

    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = new BoardModel();
        model.boardCategory = DEFAULT_BOARD_CATEGORY;
        model.boardName = shortName;
        model.boardDescription = _makeBoardDesc(model.boardName);
        model.nsfw = true;
        model.attachmentsMaxCount = 5;
        model.defaultUserName = "newfag";
        model.bumpLimit = 0;
        model.timeZoneId = "UTC";
        model.allowSubjects = true;
        model.allowNames = true;
        model.chan = getChanName();
        model.lastPage = 9;
        return model;
    }
}

