.class public final Lcom/tdm/manager/bridge/MxBridge;
.super Ljava/lang/Object;

.method private constructor <init>()V
    .registers 1

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private static firstExtra(Landroid/content/Intent;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    .registers 4

    invoke-virtual {p0, p1}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :try_second

    invoke-virtual {v0}, Ljava/lang/String;->isEmpty()Z

    move-result p1

    if-eqz p1, :done

    :try_second
    invoke-virtual {p0, p2}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    :done
    return-object v0
.end method

.method private static query(Landroid/net/Uri;Ljava/lang/String;)Ljava/lang/String;
    .registers 3

    :try_start
    invoke-virtual {p0, p1}, Landroid/net/Uri;->getQueryParameter(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0
    :try_end
    .catch Ljava/lang/Throwable; {:try_start .. :try_end} :failed

    return-object v0

    :failed
    const/4 v0, 0x0

    return-object v0
.end method

.method public static launch(Landroid/app/Activity;Landroid/content/Intent;)Z
    .registers 16

    const/4 v0, 0x0

    if-eqz p0, :return_false

    if-eqz p1, :return_false

    const/4 v1, 0x0

    const/4 v2, 0x0

    :try_start
    invoke-virtual {p1}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object v2

    if-eqz v2, :read_extra_url

    invoke-virtual {v2}, Landroid/net/Uri;->getScheme()Ljava/lang/String;

    move-result-object v3

    const-string v4, "tdmvideo"

    invoke-virtual {v4, v3}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v4

    if-eqz v4, :direct_data

    const-string v1, "url"

    invoke-static {v2, v1}, Lcom/tdm/manager/bridge/MxBridge;->query(Landroid/net/Uri;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    if-eqz v1, :read_extra_url

    invoke-static {v1}, Lcom/tdm/manager/activity/VideoPlayerActivity;->D(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    goto :url_ready

    :direct_data
    const-string v4, "http"

    invoke-virtual {v4, v3}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v4

    if-nez v4, :use_data

    const-string v4, "https"

    invoke-virtual {v4, v3}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v4

    if-nez v4, :use_data

    const-string v4, "content"

    invoke-virtual {v4, v3}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v4

    if-nez v4, :use_data

    const-string v4, "file"

    invoke-virtual {v4, v3}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :read_extra_url

    :use_data
    invoke-virtual {v2}, Landroid/net/Uri;->toString()Ljava/lang/String;

    move-result-object v1

    :read_extra_url
    if-nez v1, :url_ready

    const-string v3, "video_url"

    const-string v4, "url"

    invoke-static {p1, v3, v4}, Lcom/tdm/manager/bridge/MxBridge;->firstExtra(Landroid/content/Intent;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    :url_ready
    if-eqz v1, :return_false_in_try

    invoke-virtual {v1}, Ljava/lang/String;->isEmpty()Z

    move-result v3

    if-nez v3, :return_false_in_try

    const-string v3, "http://"

    invoke-virtual {v1, v3}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v3

    if-nez v3, :accepted_url

    const-string v3, "https://"

    invoke-virtual {v1, v3}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v3

    if-nez v3, :accepted_url

    const-string v3, "content://"

    invoke-virtual {v1, v3}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v3

    if-nez v3, :accepted_url

    const-string v3, "file://"

    invoke-virtual {v1, v3}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :return_false_in_try

    :accepted_url
    const-string v3, "video_title"

    const-string v4, "title"

    invoke-static {p1, v3, v4}, Lcom/tdm/manager/bridge/MxBridge;->firstExtra(Landroid/content/Intent;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    if-nez v3, :title_ready

    if-eqz v2, :title_ready

    invoke-static {v2, v4}, Lcom/tdm/manager/bridge/MxBridge;->query(Landroid/net/Uri;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    if-eqz v3, :title_ready

    invoke-static {v3}, Lcom/tdm/manager/activity/VideoPlayerActivity;->D(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    :title_ready
    const-string v4, "user_agent"

    const-string v5, "ua"

    invoke-static {p1, v4, v5}, Lcom/tdm/manager/bridge/MxBridge;->firstExtra(Landroid/content/Intent;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    if-nez v4, :ua_ready

    if-eqz v2, :ua_ready

    const-string v4, "user_agent"

    invoke-static {v2, v4}, Lcom/tdm/manager/bridge/MxBridge;->query(Landroid/net/Uri;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    if-nez v4, :ua_ready

    invoke-static {v2, v5}, Lcom/tdm/manager/bridge/MxBridge;->query(Landroid/net/Uri;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    :ua_ready
    const-string v5, "referer"

    invoke-virtual {p1, v5}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v6

    if-nez v6, :referer_ready

    if-eqz v2, :referer_ready

    invoke-static {v2, v5}, Lcom/tdm/manager/bridge/MxBridge;->query(Landroid/net/Uri;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v6

    :referer_ready
    const-string v5, "cookies"

    invoke-virtual {p1, v5}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v7

    if-nez v7, :cookies_ready

    if-eqz v2, :cookies_ready

    invoke-static {v2, v5}, Lcom/tdm/manager/bridge/MxBridge;->query(Landroid/net/Uri;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v7

    :cookies_ready
    new-instance v8, Landroid/content/Intent;

    const-string v9, "android.intent.action.VIEW"

    invoke-direct {v8, v9}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    invoke-static {v1}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v9

    invoke-virtual {v1}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object v10

    const-string v11, ".m3u8"

    invoke-virtual {v10, v11}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v11

    if-eqz v11, :check_dash

    const-string v10, "application/x-mpegURL"

    goto :mime_ready

    :check_dash
    const-string v11, ".mpd"

    invoke-virtual {v10, v11}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v10

    if-eqz v10, :generic_mime

    const-string v10, "application/dash+xml"

    goto :mime_ready

    :generic_mime
    const-string v10, "video/*"

    :mime_ready
    invoke-virtual {v8, v9, v10}, Landroid/content/Intent;->setDataAndType(Landroid/net/Uri;Ljava/lang/String;)Landroid/content/Intent;

    const-string v9, "com.mxtech.videoplayer.ad"

    invoke-virtual {v8, v9}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    if-eqz v3, :build_headers

    invoke-virtual {v3}, Ljava/lang/String;->isEmpty()Z

    move-result v9

    if-nez v9, :build_headers

    const-string v9, "title"

    invoke-virtual {v8, v9, v3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    :build_headers
    new-instance v9, Ljava/util/ArrayList;

    invoke-direct {v9}, Ljava/util/ArrayList;-><init>()V

    if-eqz v4, :add_referer

    invoke-virtual {v4}, Ljava/lang/String;->isEmpty()Z

    move-result v10

    if-nez v10, :add_referer

    const-string v10, "User-Agent"

    invoke-virtual {v9, v10}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    invoke-virtual {v9, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    :add_referer
    if-eqz v6, :add_cookie

    invoke-virtual {v6}, Ljava/lang/String;->isEmpty()Z

    move-result v10

    if-nez v10, :add_cookie

    const-string v10, "Referer"

    invoke-virtual {v9, v10}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    invoke-virtual {v9, v6}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    :add_cookie
    if-eqz v7, :headers_ready

    invoke-virtual {v7}, Ljava/lang/String;->isEmpty()Z

    move-result v10

    if-nez v10, :headers_ready

    const-string v10, "Cookie"

    invoke-virtual {v9, v10}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    invoke-virtual {v9, v7}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    :headers_ready
    invoke-virtual {v9}, Ljava/util/ArrayList;->isEmpty()Z

    move-result v10

    if-nez v10, :launch_mx

    invoke-virtual {v9}, Ljava/util/ArrayList;->size()I

    move-result v10

    new-array v10, v10, [Ljava/lang/String;

    invoke-virtual {v9, v10}, Ljava/util/ArrayList;->toArray([Ljava/lang/Object;)[Ljava/lang/Object;

    move-result-object v9

    check-cast v9, [Ljava/lang/String;

    const-string v10, "headers"

    invoke-virtual {v8, v10, v9}, Landroid/content/Intent;->putExtra(Ljava/lang/String;[Ljava/lang/String;)Landroid/content/Intent;

    :launch_mx
    const/4 v9, 0x1

    invoke-virtual {v8, v9}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    invoke-virtual {p0, v8}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V
    :try_end
    .catch Ljava/lang/Throwable; {:try_start .. :try_end} :return_false

    const/4 v0, 0x1

    return v0

    :return_false_in_try
    return v0

    :return_false
    return v0
.end method

.method public static launchResolved(Lcom/tdm/manager/activity/VideoPlayerActivity;)Z
    .registers 4

    if-eqz p0, :failed

    new-instance v0, Landroid/content/Intent;

    invoke-direct {v0}, Landroid/content/Intent;-><init>()V

    iget-object v1, p0, Lcom/tdm/manager/activity/VideoPlayerActivity;->q2:Ljava/lang/String;

    const-string v2, "video_url"

    invoke-virtual {v0, v2, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    iget-object v1, p0, Lcom/tdm/manager/activity/VideoPlayerActivity;->s2:Ljava/lang/String;

    const-string v2, "video_title"

    invoke-virtual {v0, v2, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    iget-object v1, p0, Lcom/tdm/manager/activity/VideoPlayerActivity;->v2:Ljava/lang/String;

    const-string v2, "user_agent"

    invoke-virtual {v0, v2, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    iget-object v1, p0, Lcom/tdm/manager/activity/VideoPlayerActivity;->t2:Ljava/lang/String;

    const-string v2, "referer"

    invoke-virtual {v0, v2, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    iget-object v1, p0, Lcom/tdm/manager/activity/VideoPlayerActivity;->u2:Ljava/lang/String;

    const-string v2, "cookies"

    invoke-virtual {v0, v2, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    invoke-static {p0, v0}, Lcom/tdm/manager/bridge/MxBridge;->launch(Landroid/app/Activity;Landroid/content/Intent;)Z

    move-result v0

    return v0

    :failed
    const/4 v0, 0x0

    return v0
.end method
