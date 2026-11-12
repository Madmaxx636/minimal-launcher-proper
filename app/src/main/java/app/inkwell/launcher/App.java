package app.inkwell.launcher;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        AppRepo.init(this);
        Widgets.init(this);
    }
}
