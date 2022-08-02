package jwtc.android.chess.ics;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;

import jwtc.android.chess.*;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.services.ClockListener;
import jwtc.android.chess.services.LocalClockApi;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

public class ICSClient extends ChessBoardActivity implements ICSListener, ResultDialogListener, AdapterView.OnItemClickListener, ClockListener {
    public static final String TAG = "ICSClient";

    public static final int REQUEST_LOGIN = 1, REQUEST_CHALLENGE = 2, REQUEST_CONFIRM = 3, REQUEST_MENU = 4;

    private ICSServer icsServer = null;
    private LocalClockApi localClockApi = new LocalClockApi();

    protected String _sConsoleEditText;
    private String _FEN = "", _sFile;
    private int  _TimeWarning, _gameStartSound, _iConsoleCharacterSize;
    private boolean _bAutoSought, _bTimeWarning, _bEndGameDialog, _bShowClockPGN,
            _notifyON, _bICSVolume, _ICSNotifyLifeCycle, isPlaying;
    private TextView _tvPlayerTop, _tvPlayerBottom, _tvPlayerTopRating, _tvPlayerBottomRating,
            _tvClockTop, _tvClockBottom, _tvBoardNum, _tvLastMove, _tvTimePerMove, _tvMoveNumber, textViewTitle;
    private TextView _tvConsole;

    private ViewSwitcher switchTurnMe, switchTurnOpp;
    private ImageButton buttonMenu;
    private EditText _editHandle, _editPwd, _editConsole, _editBoard;
    private ViewAnimator viewAnimatorRoot, viewAnimatorPlayMode;
    private LinearLayout playButtonsLayout, examineButtonsLayout;
    private ScrollView _scrollConsole;

    private Spinner _spinnerHandles;
    private ArrayAdapter<String> _adapterHandles;
    private ArrayList<String> _arrayPasswords;

    //private EditText _editPrompt;
    private ListView listChallenges, listPlayers, _listGames, _listStored, _listWelcome, listMenu;
    protected ICSMatchDlg _dlgMatch;
    private ICSPlayerDlg _dlgPlayer;
    private ICSConfirmDlg _dlgConfirm;
    private ICSGameOverDlg _dlgOver;
    private StringBuilder PGN;

    private Ringtone _ringNotification;

    private TimeZone tz = TimeZone.getDefault();

    private Matcher _matgame;

    private ArrayList<HashMap<String, String>> mapMenu = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> mapChallenges = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> mapPlayers = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> mapGames = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> mapStored = new ArrayList<HashMap<String, String>>();

    private SimpleAdapter adapterGames, adapterPlayers, adapterChallenges, adapterStored, adapterMenu;

    protected static final int VIEW_LOGIN = 0;
    protected static final int VIEW_LOADING = 1;
    protected static final int VIEW_BOARD = 2;
    protected static final int VIEW_PLAYERS = 3;
    protected static final int VIEW_GAMES = 4;
    protected static final int VIEW_CHALLENGES = 5;
    protected static final int VIEW_MENU = 6;
    protected static final int VIEW_STORED = 7;
    protected static final int VIEW_CONSOLE = 8;


    protected static final int DECREASE = 0;

    protected static int[] whiteClk = new int[200]; // PGN time clock
    protected static int[] blackClk = new int[200];

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            icsServer = ((ICSServer.LocalBinder)service).getService();

            addListeners();
            showLoginIfNotConnected();
        }

        public void onServiceDisconnected(ComponentName className) {
            OnSessionEnded();
            icsServer = null;
            Log.i(TAG, "onServiceDisconnected");
        }
    };

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        setContentView(R.layout.ics_client);

        gameApi = new ICSApi();

        afterCreate();

        isPlaying = false;

        _dlgMatch = new ICSMatchDlg(this, this, REQUEST_CHALLENGE, getPrefs());
        _dlgPlayer = new ICSPlayerDlg(this);
        _dlgConfirm = new ICSConfirmDlg(this, this, REQUEST_CONFIRM);
        _dlgOver = new ICSGameOverDlg(this);

        _iConsoleCharacterSize = 10;
        _bAutoSought = true;
        _bTimeWarning = true;
        _bEndGameDialog = true;
        _bShowClockPGN = true;

        viewAnimatorRoot = findViewById(R.id.ViewAnimatorRoot);
        viewAnimatorPlayMode = findViewById(R.id.ViewAnimatorPlayMode);

        playButtonsLayout = findViewById(R.id.LayoutPlayButtons);
        examineButtonsLayout = findViewById(R.id.LayoutExamineButtons);

        _tvPlayerTop = findViewById(R.id.TextViewTop);
        _tvPlayerBottom = findViewById(R.id.TextViewBottom);

        _tvPlayerTopRating = findViewById(R.id.TextViewICSTwoRating);
        _tvPlayerBottomRating = findViewById(R.id.TextViewICSOneRating);

        _tvClockTop = findViewById(R.id.TextViewClockTop);
        _tvClockBottom = findViewById(R.id.TextViewClockBottom);

        _tvBoardNum = findViewById(R.id.TextViewICSBoardNum);
        _tvLastMove = findViewById(R.id.TextViewICSBoardLastMove);
        _tvTimePerMove = findViewById(R.id.TextViewICSTimePerMove);
        _tvMoveNumber = findViewById(R.id.TextViewMoveNumber);

        switchTurnMe = findViewById(R.id.ImageTurnMe);
        switchTurnOpp = findViewById(R.id.ImageTurnOpp);

        _tvConsole = findViewById(R.id.TextViewConsole);
        _tvConsole.setTypeface(Typeface.MONOSPACE);

        _editHandle = findViewById(R.id.EditICSHandle);
        _editPwd = findViewById(R.id.EditICSPwd);

        Button buttonLogin = findViewById(R.id.ButICSLogin);
        buttonLogin.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {


                final String handle = _editHandle.getText().toString();
                final String pwd = _editPwd.getText().toString();

                /*
                defaultHost = chessclub.com
                hosts = chessclub.com queen.chessclub.com
                ports = 5000 23
                id = icc
                 */

                if (handle != "" && pwd != "") {
                    setLoadingView();
                    icsServer.startSession("freechess.org", 23, handle, pwd, "fics% ");
                } else {
                    if (handle == "") {
                        globalToast(getString(R.string.msg_ics_enter_handle));
                    }
                    if (handle != "guest" && pwd == "") {
                        globalToast(getString(R.string.msg_ics_enter_password));
                    }
                }
            }
        });

        Button buttonResign = findViewById(R.id.ButtonResign);
        buttonResign.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendString("resign");
            }
        });

        Button buttonDraw = findViewById(R.id.ButtonDraw);
        buttonDraw.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendString("draw");
            }
        });

        Button buttonFlag = findViewById(R.id.ButtonFlag);
        buttonFlag.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendString("flag");
            }
        });

        OnClickListener takeBackListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendString("revert");
            }
        };

        ImageButton buttonTakeBack = findViewById(R.id.ButtonTakeBack);
        buttonTakeBack.setOnClickListener(takeBackListener);

        ImageButton buttonRevert = findViewById(R.id.ButtonICSExamineRevert);
        buttonRevert.setOnClickListener(takeBackListener);

        ImageButton buttonBackward = findViewById(R.id.ButtonICSExamineBackward);
        buttonBackward.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendString("backward");
            }
        });

        ImageButton buttonForward = findViewById(R.id.ButtonICSExamineForward);
        buttonForward.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendString("forward");
            }
        });

        textViewTitle = findViewById(R.id.TextViewTitle);

        String[] from = { "menu_item" };
        int[] to = { R.id.MenuText };
        adapterMenu = new SimpleAdapter(this, mapMenu, R.layout.menu_item, from, to);

        listMenu = findViewById(R.id.ListMenu);
        listMenu.setAdapter(adapterMenu);
        listMenu.setOnItemClickListener(this);

        buttonMenu = findViewById(R.id.ButtonMenu);
        buttonMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setMenuView();
            }
        });

        adapterChallenges = new SimpleAdapter(ICSClient.this, mapChallenges, R.layout.ics_seek_row,
                new String[]{"text_game", "text_name", "text_rating"}, new int[]{R.id.text_game, R.id.text_name, R.id.text_rating});
//
        listChallenges = findViewById(R.id.ListChallenges);
        listChallenges.setAdapter(adapterChallenges);
        listChallenges.setOnItemClickListener(this);
//
        adapterPlayers = new SimpleAdapter(ICSClient.this, mapPlayers, R.layout.ics_player_row,
                new String[]{"text_name", "text_rating"},
                new int[]{R.id.text_name, R.id.text_rating});

        listPlayers = findViewById(R.id.ListPlayers);
        listPlayers.setAdapter(adapterPlayers);
        listPlayers.setOnItemClickListener(this);

        adapterGames = new SimpleAdapter(ICSClient.this, mapGames, R.layout.ics_game_row,
                new String[]{"nr", "text_name1", "text_name2", "text_rating1", "text_rating2", "text_time1", "text_time2", "text_type"},
                new int[]{R.id.nr, R.id.text_name1, R.id.text_name2, R.id.text_rating1, R.id.text_rating2, R.id.text_time1, R.id.text_time2, R.id.text_type});

        _listGames = findViewById(R.id.ListGames);
        _listGames.setAdapter(adapterGames);
        _listGames.setOnItemClickListener(this);

        adapterStored = new SimpleAdapter(ICSClient.this, mapStored, R.layout.ics_stored_row,
                new String[]{"nr_stored", "color_stored", "text_name_stored", "available_stored"},
                new int[]{R.id.nr_stored, R.id.color_stored, R.id.text_name_stored, R.id.available_stored});

//        _listStored = (ListView) findViewById(R.id.ICSStored);
//        _listStored.setAdapter(adapterStored);
//         _listStored.setOnItemClickListener(this);


//        ImageButton butQuick2 = (ImageButton) findViewById(R.id.ButtonICSConsoleQuickCmd);
//        if (butQuick2 != null) { // crashes reported on this being null
//            butQuick2.setOnClickListener(new OnClickListener() {
//                public void onClick(View arg0) {
//                    //showMenu();
//                    openOptionsMenu();
//                }
//            });
//        }


//        _spinnerHandles = (Spinner) findViewById(R.id.SpinnerLoginPresets);
//
//        _spinnerHandles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//
//            @Override
//            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
//                _editHandle.setText(_spinnerHandles.getSelectedItem().toString());
//                _editPwd.setText(_arrayPasswords.get(position));
//                if (_arrayPasswords.get(position).length() < 2) {
//                    _editPwd.setText("");
//                }
//                Log.d(TAG, _spinnerHandles.getSelectedItem().toString());
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parentView) {
//                Log.d(TAG, "nothing selected in spinner");
//            }
//        });

//        final Handler actionHandler = new Handler();
//        final Runnable runnable = new Runnable() {
//            @Override
//            public void run() {
//                new AlertDialog.Builder(ICSClient.this)
//                        .setTitle("Delete entry")
//                        .setMessage("Are you sure you want to delete " + _spinnerHandles.getSelectedItem().toString() + "?")
//                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int which) {
//                                String newData = _spinnerHandles.getSelectedItem().toString();
//                                _adapterHandles.remove(newData);
//                                _adapterHandles.notifyDataSetChanged();
//                                _arrayPasswords.remove(_spinnerHandles.getSelectedItemPosition());
//                                _editHandle.setText("");
//                                _editPwd.setText("");
//                            }
//                        })
//                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int which) {
//                                // do nothing
//                            }
//                        })
//                        .setIcon(android.R.drawable.ic_dialog_alert)
//                        .show();
//            }
//        };
//
//        _spinnerHandles.setOnTouchListener(new View.OnTouchListener() { // simulate long press on spinner
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                if (event.getAction() == MotionEvent.ACTION_DOWN) {
//                    actionHandler.postDelayed(runnable, 650);
//                } else if (event.getAction() == MotionEvent.ACTION_UP) {
//                    actionHandler.removeCallbacks(runnable);
//                }
//                return false;
//            }
//        });
        /////////////////////


        View.OnKeyListener okl = new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) ||
                        event.getAction() == EditorInfo.IME_ACTION_DONE
                ) {
                    // Perform action on key press
                    EditText et = (EditText) v;
                    _sConsoleEditText = et.getText().toString();

                    sendString(_sConsoleEditText);
                    et.setText("");

                    return true;
                }
                return false;
            }
        };

        _editConsole = (EditText) findViewById(R.id.EditICSConsole);
        if (_editConsole != null) {
            _editConsole.setTextColor(getResources().getColor(android.R.color.white));
            _editConsole.setSingleLine(true);
            _editConsole.setOnKeyListener(okl);
        }

        _scrollConsole = findViewById(R.id.ScrollICSConsole);

//        _editBoard = (EditText) findViewById(R.id.EditICSBoard);
//        if (_editBoard != null) {
//            _editBoard.setSingleLine(true);
//            _editBoard.setOnKeyListener(okl);
//        }
//
        Button butReg = findViewById(R.id.ButICSRegister);
        if (butReg != null) {
            butReg.setOnClickListener(new View.OnClickListener() {
                public void onClick(View arg0) {
                    try {
                        Intent i = new Intent();
                        i.setAction(Intent.ACTION_VIEW);
                        i.setData(Uri.parse("http://www.freechess.org/Register/index.html"));
                        startActivity(i);
                    } catch (Exception ex) {

                        doToast("Could not go to registration page");
                    }
                }
            });
        }


        _ringNotification = null;

//        switchToLoginView();

        localClockApi.addListener(this);

        Log.i("ICSClient", "onCreate");
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            ICSApi icsApi = (ICSApi)gameApi;
            int rootView = viewAnimatorRoot.getDisplayedChild();

            if (rootView == VIEW_BOARD) {
                int viewMode = icsApi.getViewMode();

                if (viewMode == ICSApi.VIEW_PLAY) {
                    if (isPlaying) {
                        new AlertDialog.Builder(ICSClient.this)
                            .setTitle(ICSClient.this.getString(R.string.ics_menu_abort) + "?")
                            .setPositiveButton(getString(R.string.alert_yes),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            dialog.dismiss();
                                            sendString("abort");

                                            setMenuView();
                                        }
                                    })
                            .setNegativeButton(getString(R.string.alert_no), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            }).show();
                    } else {
                        setMenuView();
                    }
                    return true;
                }
                else {
                    switch(viewMode) {
                        case ICSApi.VIEW_OBSERVE:
                            sendString("unobserve");
                            break;
                        case ICSApi.VIEW_EXAMINE:
                            sendString("unexamine");
                            break;
                    }
                    setMenuView();
                }
                return true;
            } else {
                stopSession(R.string.ics_quit);
                return true;
            }
        } else {
            finish();
        }

        return super.onKeyDown(keyCode, event);
    }

    public void stopSession(int resource) {

        if (isFinishing()) {
            return;
        }
        new AlertDialog.Builder(ICSClient.this)
            .setTitle(resource)
//            .setMessage(resource)
            .setPositiveButton(R.string.alert_ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        finish();
                    }
                })
            .show();
    }

    protected void makeGamePGN(String sEnd) {

        String sBeg;

        sEnd = sEnd.trim().replaceAll(" +", " ");
        sEnd = sEnd.replaceAll("\\{.*\\}", "");

        String site = "FICS";
        String _FEN1, _FEN2;

        sBeg = sEnd.substring(sEnd.indexOf("1."), sEnd.length());

        if (_bShowClockPGN) {
            sBeg = convertTimeUsedToClock(sBeg);
        } else {
            sBeg = sBeg.replaceAll("\\s*\\([^\\)]*\\)\\s*", " ");  // gets rid of timestamp and parentheses
        }

        //Log.d(TAG, "\n" + sBeg);

        PGN = new StringBuilder("");
        PGN.append("[Event \"" + _matgame.group(7) + "\"]\n");
        PGN.append("[Site \"" + site + "\"]\n");
        PGN.append("[Date \"" + _matgame.group(5) + _matgame.group(6) + "\"]\n");
        PGN.append("[White \"" + _matgame.group(1) + "\"]\n");
        PGN.append("[Black \"" + _matgame.group(3) + "\"]\n");
        PGN.append("[Result \"" + _matgame.group(12) + "\"]\n");
        PGN.append("[WhiteElo \"" + _matgame.group(2) + "\"]\n");
        PGN.append("[BlackElo \"" + _matgame.group(4) + "\"]\n");
        String _minutestoseconds = Integer.toString(Integer.parseInt(_matgame.group(8)) * 60);
        PGN.append("[TimeControl \"" + _minutestoseconds + "+" +
                _matgame.group(9) + "\"]\n");

        if (!_FEN.equals("")) {  // As for now, used for Chess960 FEN.
            _FEN1 = _FEN.substring(0, _FEN.indexOf(" "));
            _FEN2 = _FEN.substring(_FEN.indexOf("P") + 9, _FEN.indexOf("W") - 1);
            if (!_FEN1.equals("rnbqkbnr") || !_FEN2.equals("RNBQKBNR")) {
                PGN.append("[FEN \"" + _FEN1 + "/pppppppp/8/8/8/8/PPPPPPPP/" + _FEN2 + " w KQkq - 0 1" + "\"]\n");
            }
            _FEN = "";  // reset to capture starting FEN for next game
        }

        PGN.append(sBeg + "\n\n");

//        saveGameSDCard();

//        _dlgOver.updateGameResultText(_matgame.group(11)); // game result message sent to dialog
//
//        _dlgOver.setWasPlaying(get_view().getOpponent().length() > 0);
//        _dlgOver.show();
        //_dlgOver.prepare();

    }

    private String convertTimeUsedToClock(String sBeg) {

        int time = Integer.parseInt(_matgame.group(8)), incTime = Integer.parseInt(_matgame.group(9));
        ;

        int incTurn = 1;
        boolean turn = true;

        time = time * 60; // convert minutes to seconds

        whiteClk[0] = time;  // initial start time
        blackClk[0] = time;

        Pattern p = Pattern.compile("\\((\\d+):(\\d+)\\)");
        Matcher m = p.matcher(sBeg);

        while (m.find()) {

            int min = 0, sec = 0, time1 = 0;

            min = min + Integer.parseInt(m.group(1)) * 60;
            sec = Integer.parseInt(m.group(2));
            time1 = min + sec;

            if (turn) {
                time1 = whiteClock(time1, incTurn, incTime);
                turn = false;
            } else {
                time1 = blackClock(time1, incTurn, incTime);
                turn = true;
                incTurn++;
            }

            String clock1 = convertSecondsToClock(time1);

            sBeg = sBeg.replaceFirst("\\((\\d+):(\\d+)\\)", clock1);  // replace time used with clock

        }

        return sBeg;
    }

    private int whiteClock(int time1, int incTurn, int incTime) {

        whiteClk[incTurn] = whiteClk[incTurn - 1] - time1;
        if (incTurn > 1) {
            whiteClk[incTurn] = whiteClk[incTurn] + incTime;
        }

        return whiteClk[incTurn];
    }

    private int blackClock(int time1, int incTurn, int incTime) {

        blackClk[incTurn] = blackClk[incTurn - 1] - time1;
        if (incTurn > 1) {
            blackClk[incTurn] = blackClk[incTurn] + incTime;
        }

        return blackClk[incTurn];
    }

    private String convertSecondsToClock(int time1) {
        String clock, timeString;
        int hours, minutes, seconds;
        hours = time1 / 3600;
        minutes = (time1 % 3600) / 60;
        seconds = time1 % 60;

        timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        clock = "{[%clk " + timeString + "]}";

        return clock;
    }

    protected void gameOverToast(String line) {  // send toast result of the game

        String text = "";
        text = line.substring(line.indexOf(")") + 2, line.indexOf("}"));  // gets name and state of name

        if (line.contains("} 1-0") || line.contains("} 0-1")) {


            if (line.indexOf(" resigns} ") > 0) {  // make translation friendly
                text = text.replace("resigns", getString(R.string.state_resigned));

            } else if (line.indexOf("checkmated") > 0) {
                text = text.replace("checkmated", getString(R.string.state_mate));

            } else if (line.indexOf("forfeits on time") > 0) {
                text = text.replace("forfeits on time", getString(R.string.state_time));

            } else {
                text = getString(R.string.ics_game_over);
            }
        } else if (line.contains("} 1/2-1/2")) {  // draw
            gameToast(String.format(getString(R.string.ics_game_over_format), getString(R.string.state_draw)));

            return;
        }

        gameToast(String.format(getString(R.string.ics_game_over_format), text));

    }

    private void confirmShow(String title, String text, String sendstring) {
        _dlgConfirm.setSendString(sendstring);
        _dlgConfirm.setText(title, text);
        _dlgConfirm.show();
    }

    public void gameToast(final String text) {
        Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }


    public void globalToast(final String text) {
        Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }

    @Override
    public boolean requestMove(int from, int to) {
        highlightedPositions.clear();
        highlightedPositions.add(from);
        highlightedPositions.add(to);

        ICSApi icsApi = (ICSApi) gameApi;
        if (isPlaying) {
            if (icsApi.getMyTurn() == icsApi.getTurn()) {
                // @TODO promotion
                // if (jni.pieceAt(BoardConstants.WHITE, from)
                String sMove = Pos.toString(from) + "-" + Pos.toString(to);
                sendString(sMove);
            } else  {
                setPremove(from, to);
            }
        } else {
            String sMove = Pos.toString(from) + "-" + Pos.toString(to);
            sendString(sMove);
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        invalidateOptionsMenu(); // update OptionsMenu

        SharedPreferences prefs = this.getPrefs();

        _editHandle.setText(prefs.getString("ics_handle", "guest"));
        _editPwd.setText(prefs.getString("ics_password", ""));

        _iConsoleCharacterSize = Integer.parseInt(prefs.getString("ICSConsoleCharacterSize", "10"));
        _tvConsole.setTextSize(_iConsoleCharacterSize);

        _bAutoSought = prefs.getBoolean("ICSAutoSought", true);

        _bTimeWarning = prefs.getBoolean("ICSTimeWarning", true);
        _TimeWarning = Integer.parseInt(prefs.getString("ICSTimeWarningsecs", "10"));

        _bEndGameDialog = prefs.getBoolean("ICSEndGameDialog", true);

        _bShowClockPGN = prefs.getBoolean("ICSClockPGN", true);

        _bICSVolume = prefs.getBoolean("ICSVolume", true);


        // get rid of notification for tap to play
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(0); // 0 is notification id
        _ICSNotifyLifeCycle = false;

        _gameStartSound = Integer.parseInt(prefs.getString("ICSGameStartSound", "1"));

        _notifyON = prefs.getBoolean("ICSGameStartBringToFront", true);

        /////////////////////////////////////////////
//        _adapterHandles = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
//        _arrayPasswords = new ArrayList<String>();
//
//        if (_ficsHandle != null) {
//            _adapterHandles.add(_ficsHandle);
//            _arrayPasswords.add(_ficsPwd);
//        }
//
//        try {
//            JSONArray jArray = new JSONArray(prefs.getString("ics_handle_array", "guest"));
//            JSONArray jArrayPasswords = new JSONArray(prefs.getString("ics_password_array", ""));
//            for (int i = 0; i < jArray.length(); i++) {
//                _adapterHandles.add(jArray.getString(i));
//                _arrayPasswords.add(jArrayPasswords.getString(i));
//            }
//        } catch (JSONException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//
//        int t = 0;
//        boolean g = false;
//        for (int i = 0; i < _adapterHandles.getCount(); i++) {
//            String x = _adapterHandles.getItem(i).toString();
//            if (x.equals(_ficsHandle)) {
//                t++;
//            }
//            if (x.equals("guest")) {
//                g = true;  // flag to check if guest is in the spinner
//            }
//        }
//        if (t > 1) {  // work around for remove - delete every occurrence and add latest - this will add latest password
//            while (t > 0) {
//                _adapterHandles.remove(_ficsHandle);
//                _arrayPasswords.remove(_adapterHandles.getPosition(_ficsHandle) + 1);
//                t--;
//            }
//            _adapterHandles.add(_ficsHandle);
//            _arrayPasswords.add(_ficsPwd);
//        }
//
//        if (g == false) {
//            _adapterHandles.add("guest"); // if no guest then add guest
//            _arrayPasswords.add(" ");
//        }
//
//        _spinnerHandles.setAdapter(_adapterHandles);
//
//        _spinnerHandles.setSelection(_adapterHandles.getPosition(_ficsHandle));
//
//
//        /////////////////////////////////////////////////////////////////
//
//        if (_ficsHandle == null) {
//            _ficsHandle = "guest";
//            _ficsPwd = "";
//        }


        // @TODO
        String sTmp = prefs.getString("NotificationUri", null);
        if (sTmp == null) {
            _ringNotification = null;
        } else {
            Uri tmpUri = Uri.parse(sTmp);
            _ringNotification = RingtoneManager.getRingtone(this, tmpUri);
        }

        addListeners();
        showLoginIfNotConnected();
    }

    public void addListeners() {
        if (icsServer != null) {
            icsServer.addListener(this);
        }
    }

    public void removeListeners() {
        if (icsServer != null) {
            icsServer.removeListener(this);
        }
    }

    public void showLoginIfNotConnected() {
        if (!isConnected()) {
            setLoginView();
        }
    }

    public boolean is_bTimeWarning() {
        return _bTimeWarning;
    }

    public int get_TimeWarning() {
        return _TimeWarning;
    }

    public int get_gameStartSound() {
        return _gameStartSound;
    }

    public String get_whiteHandle() {
        try {
            return _matgame.group(1);
        } catch (Exception e) { // return _matgame match
            return "";
        }
    }

    public String get_whiteRating() {
        try {
            return _matgame.group(2);
        } catch (Exception e) {
            return "";
        }
    }

    public String get_blackHandle() {
        try {
            return _matgame.group(3);
        } catch (Exception e) {
            return "";
        }
    }

    public String get_blackRating() {
        try {
            return _matgame.group(4);
        } catch (Exception e) {
            return "";
        }
    }

    public boolean isConnected() {
        if (this.icsServer != null) {
            return this.icsServer.isConnected();
        }
        return false;
    }

    public void notificationAPP() {

        if (_notifyON && _ICSNotifyLifeCycle) {

            Intent intent = new Intent(this, ICSClient.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

            NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

            Notification.Builder builder = new Notification.Builder(this);
            builder.setContentIntent(pendingIntent)
                    .setSmallIcon(R.drawable.ic_logo)
                    .setWhen(System.currentTimeMillis())
                    .setAutoCancel(true)
                    .setLights(Color.CYAN, 100, 100)
                    .setContentTitle(getString(R.string.ics_notification_title))
                    .setContentText(getString(R.string.ics_notification_text));

            Notification notification = builder.getNotification();

            notificationManager.notify(0, notification);

        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");

        removeListeners();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putString("ics_handle", _editHandle.getText().toString());
        editor.putString("ics_password", _editPwd.getText().toString());

//        JSONArray jArray = new JSONArray();
//        JSONArray jArrayPasswords = new JSONArray();
//        for (int i = 0; i < _adapterHandles.getCount(); i++) {
//            jArray.put(_adapterHandles.getItem(i));
//            jArrayPasswords.put(_arrayPasswords.get(i));
//        }
//        editor.putString("ics_handle_array", jArray.toString());
//        editor.putString("ics_password_array", jArrayPasswords.toString());

        editor.putBoolean("ICSVolume", _bICSVolume);

        editor.commit();

        _ICSNotifyLifeCycle = true;

        super.onPause();
    }

    @Override
    protected void onRestart() {
        Log.i(TAG, "onRestart");
        super.onRestart();
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();

        if (icsServer == null) {
            if (bindService(new Intent(ICSClient.this, ICSServer.class), mConnection, Context.BIND_AUTO_CREATE)) {
                Log.i(TAG, "Bind to ICSServer");
            } else {
                globalToast("Could not init remote chess process");
                Log.e(TAG, "Error: The requested service doesn't exist, or this client isn't allowed access to it.");
            }
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();

    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");

        removeListeners();

        unbindService(mConnection);
        icsServer = null;

        super.onDestroy();
    }

    public void sendString(String s) {
        if (icsServer != null && !icsServer.sendString(s)) {
            try {
                new AlertDialog.Builder(ICSClient.this)
                        .setTitle(R.string.title_error)
                        .setMessage(getString(R.string.ics_lost_connection))
                        .setPositiveButton(getString(R.string.alert_ok),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        dialog.dismiss();
                                        finish();
                                    }
                                })
                        .show();
            } catch (Exception ex) {
                Log.e(TAG, ex.toString());
            }
        }
    }

    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        if (arg0 == listChallenges) {
            if (mapChallenges.size() > arg2) {
                HashMap<String, String> m = mapChallenges.get(arg2);
                Log.i("onItemClick", "item " + m.get("play"));
                sendString("play " + m.get("play"));
            }
        } else if (arg0 == listPlayers) {
            if (mapPlayers.size() > arg2) {
                HashMap<String, String> m = mapPlayers.get(arg2);
                Log.i("onItemClick", "item " + m.get("text_name"));
                _dlgPlayer.opponentName(m.get("text_name"));
                _dlgPlayer._tvPlayerListConsole.setText("");  // clear TextView
                _dlgPlayer.show();

            }
        } else if (arg0 == _listGames) {
            if (mapGames.size() > arg2) {
                HashMap<String, String> m = mapGames.get(arg2);
                Log.i("onItemClick", "item " + m.get("text_name1"));
                sendString("observe " + m.get("text_name1"));
                sendString("refresh");
            }
        } else if (arg0 == _listStored) {
            if (mapStored.size() > arg2) {
                HashMap<String, String> m = mapStored.get(arg2);
                Log.i("onItemClick", "item " + m.get("text_name_stored"));
                sendString("match " + m.get("text_name_stored"));
            }
        } else if (arg0 == listMenu) {
            if (mapMenu.size() > arg2) {
                HashMap<String, String> m = mapMenu.get(arg2);
                String selected = m.get("menu_item");

                if (selected.equals(getString(R.string.ics_menu_games))) {
                    loadGames();
                } else if (selected.equals(getString(R.string.ics_menu_top_blitz))) {
                    sendString("obs /b");
                } else if (selected.equals(getString(R.string.ics_menu_players))) {
                    loadPlayers();
                } else if (selected.equals(getString(R.string.ics_menu_challenges))) {
                    loadChallenges();
                } else if (selected.equals(getString(R.string.ics_menu_seek))) {
                    _dlgMatch._rbSeek.setChecked(true);
                    _dlgMatch._rbSeek.performClick();
                    _dlgMatch.show();
                } else if (selected.equals(getString(R.string.ics_menu_top_blitz))) {
                    sendString("obs /b");
                } else if (selected.equals(getString(R.string.ics_menu_top_standard))) {
                    sendString("obs /s");
                } else if (selected.equals(getString(R.string.ics_menu_puzzlebot_mate))) {
                    sendString("tell puzzlebot getmate");
                } else if (selected.equals(getString(R.string.ics_menu_puzzlebot_tactics))) {
                    sendString("tell puzzlebot gettactics");
                } else if (selected.equals(getString(R.string.ics_menu_puzzlebot_study))) {
                    sendString("tell puzzlebot getstudy");
                } else if (selected.equals(getString(R.string.ics_menu_console))) {
                    setConsoleView();
                } else if (selected.equals(getString(R.string.menu_prefs))) {
                    Intent i = new Intent();
                    i.setClass(ICSClient.this, ICSPrefs.class);
                    startActivity(i);
                } else if (selected.equals(getString(R.string.menu_help))) {
                    Intent i = new Intent();
                    i.setClass(ICSClient.this, HtmlActivity.class);
                    i.putExtra(HtmlActivity.HELP_MODE, "help_online");
                    startActivity(i);
                } else {
                    // assume a custom command
                    sendString(selected);
                }
            }
        }
    }

    public void setMenuView() {
        mapMenu.clear();

        final int[] resources = {
                R.string.ics_menu_challenges,
                R.string.ics_menu_seek,
                R.string.ics_menu_games,
                R.string.ics_menu_puzzlebot_mate,
                R.string.ics_menu_puzzlebot_tactics,
                R.string.ics_menu_puzzlebot_study,
                R.string.ics_menu_players,
                R.string.ics_menu_top_blitz,
                R.string.ics_menu_top_standard,
                R.string.menu_prefs,
                R.string.ics_menu_console,
                R.string.menu_help,

                // endgamebot dialog (kbnk, ...)?
        };

        for (int i = 0; i < resources.length; i++) {
            final int index = i;
            mapMenu.add(new HashMap<String, String>() {{ put("menu_item", getString(resources[index])); }}) ;
        }

        try {
            SharedPreferences prefs = this.getPrefs();
            final JSONArray jArray = new JSONArray(prefs.getString("ics_custom_commands", CustomCommands.DEFAULT_COMMANDS));

            for (int i = 0; i < jArray.length(); i++) {
                final int index = i;
                try {
                    mapMenu.add(new HashMap<String, String>() {{ put("menu_item", jArray.getString(index)); }}) ;
                } catch (JSONException e) {
                }
            }
        } catch (JSONException e) {
        }

        adapterMenu.notifyDataSetChanged();

        viewAnimatorRoot.setDisplayedChild(VIEW_MENU);
        buttonMenu.setVisibility(View.GONE);

        textViewTitle.setText(icsServer != null  ? icsServer.getHandle() : "--");
    }

    public void setLoadingView() {
        viewAnimatorRoot.setDisplayedChild(VIEW_LOADING);
        buttonMenu.setVisibility(View.GONE);
        textViewTitle.setText("");
    }

    public void setPlayerView() {
        viewAnimatorRoot.setDisplayedChild(VIEW_PLAYERS);
        buttonMenu.setVisibility(View.VISIBLE);
        textViewTitle.setText(R.string.ics_menu_players);
    }

    public void setGamesView() {
        viewAnimatorRoot.setDisplayedChild(VIEW_GAMES);
        buttonMenu.setVisibility(View.VISIBLE);
        textViewTitle.setText(R.string.ics_menu_games);
    }

    public void setChallengeView() {
        viewAnimatorRoot.setDisplayedChild(VIEW_CHALLENGES);
        buttonMenu.setVisibility(View.VISIBLE);
        textViewTitle.setText(R.string.ics_menu_challenges);
    }

    public void setBoardView() {
        viewAnimatorRoot.setDisplayedChild(VIEW_BOARD);
        buttonMenu.setVisibility(View.GONE);
        textViewTitle.setText("");
    }

    public void setLoginView() {
        viewAnimatorRoot.setDisplayedChild(VIEW_LOGIN);
        buttonMenu.setVisibility(View.GONE);
        textViewTitle.setText("");
    }

    public void setConsoleView() {
        viewAnimatorRoot.setDisplayedChild(VIEW_CONSOLE);
        buttonMenu.setVisibility(View.VISIBLE);
        textViewTitle.setText("");
    }

    public void loadChallenges() {
        sendString("sought");
        setChallengeView();
    }

    public void loadGames() {
        setLoadingView();
        sendString("games");
    }

    public void loadPlayers() {
        setLoadingView();
        sendString("players");
    }

    public void soundNotification() {
        if (_ringNotification != null) {
            _ringNotification.play();
        }
    }

    @Override
    public void OnDialogResult(int requestCode, Bundle data) {
        switch (requestCode) {
            case REQUEST_CHALLENGE:
                String challenge = data.getString("challenge");
                if (challenge != null) {
                    sendString(challenge);
                    doToast(getString(R.string.toast_challenge_posted));
                }
                break;

        }
    }

    public class ComparatorHashName implements java.util.Comparator<HashMap<String, String>> {
        public int compare(HashMap<String, String> a, HashMap<String, String> b) {
            String sA = a.get("text_name"), sB = b.get("text_name");
            if (sA != null && sB != null) {
                return sA.compareToIgnoreCase(sB);
            }
            return 0;
        }
    }

    public class ComparatorHashRating implements java.util.Comparator<HashMap<String, String>> {
        public int compare(HashMap<String, String> a, HashMap<String, String> b) {
            String sA = a.get("text_rating"), sB = b.get("text_rating");
            if (sA != null && sB != null) {
                return -1 * sA.compareToIgnoreCase(sB);
            }
            return 0;
        }
    }

    @Override
    public void OnLoginSuccess() {
        sendString("style 12");
        sendString("-channel 4"); // guest
        sendString("-channel 53"); // guest chat
        sendString("set kibitz 1"); // for puzzlebot
        sendString("set gin 0"); // current server game results - turn off - some clients turn it on
        sendString("set tzone " + tz.getDisplayName(false, TimeZone.SHORT));  // sets timezone

        // _handle = handle;

        setMenuView();
    }

    @Override
    public void OnLoginFailed(String error) {
        doToast("Could not log you in " + error);
        setLoginView();
    }

    @Override
    public void OnLoggingIn() {
        doToast("Logging you in");
    }

    @Override
    public void OnSessionEnded() {
        setLoginView();
    }

    @Override
    public void OnError() {
        Log.i(TAG, "OnError");
        new AlertDialog.Builder(ICSClient.this)
            .setTitle(ICSClient.this.getString(R.string.ics_error))
            .setPositiveButton(getString(R.string.alert_ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.dismiss();
                            finish();
                        }
                    })
            .show();
    }

    @Override
    public void OnPlayerList(ArrayList<HashMap<String, String>> playerList) {
        mapPlayers.clear();
        for (int i = 0; i < playerList.size(); i++) {
            mapPlayers.add(playerList.get(i));
        }
        Collections.sort(mapPlayers, new ComparatorHashRating());
        adapterPlayers.notifyDataSetChanged();

        setPlayerView();
    }

    @Override
    public void OnBoardUpdated(String gameLine, String handle) {
        ICSApi icsApi = (ICSApi) gameApi;
        icsApi.parseGame(gameLine, handle);

        setBoardView();

        int viewMode = icsApi.getViewMode();

        playButtonsLayout.setVisibility(viewMode == ICSApi.VIEW_PLAY ? View.VISIBLE : View.GONE);
        examineButtonsLayout.setVisibility(viewMode == ICSApi.VIEW_EXAMINE ? View.VISIBLE : View.GONE);

        String lastMove = icsApi.getLastMove();
        if (spSound != null) {
            if (lastMove.contains("+") || lastMove.contains("#")) {
                spSound.play(soundCheck, fVolume, fVolume, 1, 0, 1);
            } else if (lastMove.contains("x")) {
                spSound.play(soundCapture, fVolume, fVolume, 1, 0, 1);
            } else {
                spSound.play(soundMove, fVolume, fVolume, 1, 0, 1);
            }
        }
    }

    @Override
    public void OnChallenged(HashMap<String, String> challenge) {
        new AlertDialog.Builder(ICSClient.this)
            .setTitle(ICSClient.this.getString(R.string.title_challenge))
            .setMessage(challenge.get("opponent") +
                    " [" + challenge.get("rating") + "]\n " +
                    " challenges you for a " +
                    challenge.get("minutes") + " min.+" +
                    challenge.get("seconds") + " s " +
                    challenge.get("type") + " " +
                    challenge.get("num") + ".\n" +
                    "Do you wish to accept?")
            .setPositiveButton(getString(R.string.alert_yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            sendString("accept");
                            dialog.dismiss();
                        }
                    })
            .setNegativeButton(getString(R.string.alert_no), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            })
            .show();
    }

    @Override
    public void OnIllegalMove() {
        rebuildBoard();
        highlightedPositions.clear();
        updateSelectedSquares();
    }

    @Override
    public void OnSeekNotAvailable() {
        globalToast("That seek is not available");
    }

    @Override
    public void OnPlayGameStarted(String whiteHandle, String blackHandle, String whiteRating, String blackRating) {
        globalToast("Game initialized");
        Log.d(TAG, "OnPlayGameStarted " + whiteHandle + blackHandle + whiteRating + blackRating);
        if (icsServer != null) {
            if (icsServer.getHandle() == whiteHandle || icsServer.getHandle() == blackHandle) {
                isPlaying = true;
                if (spSound != null) {
                    spSound.play(soundNewGame, fVolume, fVolume, 1, 0, 1);
                }
            }
        }
    }

    @Override
    public void OnGameNumberUpdated(int number) {
        if (number > 0) {
//            get_view().setGameNum(number);
        }
    }

    @Override
    public void OnOpponentRequestsAbort() {
        confirmShow(getString(R.string.title_offers_abort), getString(R.string.ics_offers_abort), "abort");
    }

    @Override
    public void OnOpponentRequestsAdjourn() {
        confirmShow(getString(R.string.title_offers_adjourn), getString(R.string.ics_offers_adjourn), "adjourn");
    }

    @Override
    public void OnOpponentOffersDraw() {
        confirmShow(getString(R.string.title_offers_draw), getString(R.string.ics_offers_draw), "draw");
    }

    @Override
    public void OnOpponentRequestsTakeBack() {
        confirmShow(getString(R.string.title_offers_takeback), getString(R.string.ics_offers_takeback), "accept");
    }

    @Override
    public void OnAbortConfirmed() {
        gameToast("Game aborted by mutual agreement");
        isPlaying = false;
//        get_view().setStopPlaying();
    }

    @Override
    public void OnPlayGameResult(String message) {
        gameToast(message);
    }

    @Override
    public void OnPlayGameStopped() {
//        get_view().setStopPlaying();
        isPlaying = false;
    }

    @Override
    public void OnYourRequestSended() {
        gameToast(getString(R.string.ics_request_sent));
    }

    @Override
    public void OnChatReceived() {

    }

    @Override
    public void OnResumingAdjournedGame() {
        gameToast("Resuming adjourned game");
        isPlaying = true;
    }

    @Override
    public void OnAbortedOrAdjourned() {
        gameToast("Game stopped (aborted or adjourned)");
        isPlaying = false;
    }

    @Override
    public void OnObservingGameStarted() {
        globalToast("Observing a game");
    }

    @Override
    public void OnObservingGameStopped() {
        globalToast("No longer observing the game");
    }

    @Override
    public void OnPuzzleStarted() {
        globalToast("Puzzle started");
    }

    @Override
    public void OnPuzzleStopped() {
        globalToast("Puzzle stopped");
    }

    @Override
    public void OnPuzzleSolved() {
        globalToast("Puzzle solved");
    }

    @Override
    public void OnExaminingGameStarted() {
        globalToast("Examining a game");
    }

    @Override
    public void OnExaminingGameStopped() {
        globalToast("No longer examining the game");
    }

    @Override
    public void OnSoughtResult(ArrayList<HashMap<String, String>> soughtList) {
        mapChallenges.clear();
        for (int i = 0; i < soughtList.size(); i++) {
            mapChallenges.add(soughtList.get(i));
        }
        adapterChallenges.notifyDataSetChanged();
    }

    @Override
    public void OnGameListResult(ArrayList<HashMap<String, String>> games) {
        mapGames.clear();
        for (int i = 0; i < games.size(); i++) {
            mapGames.add(games.get(i));
        }

        adapterGames.notifyDataSetChanged();
        setGamesView();
    }

    @Override
    public void OnStoredListResult(ArrayList<HashMap<String, String>> games) {
        mapStored.clear();
        for (int i = 0; i < games.size(); i++) {
            mapStored.add(games.get(i));
        }
        adapterStored.notifyDataSetChanged();
    }

    @Override
    public void OnGameEndedResult(int state) {
        int res = chessStateToR(state);

        highlightedPositions.clear();
        updateSelectedSquares();
        localClockApi.stopClock();

        if (_bEndGameDialog) {
            _dlgOver.setHandle(icsServer.getHandle());
            _dlgOver.setWasPlaying(isPlaying);
            _dlgOver.updateGameResultText(getString(res));
            _dlgOver.show();
        } else {
            gameToast(getString(res));
        }

        isPlaying = false;
    }

    @Override
    public void OnConsoleOutput(String buffer) {
         addConsoleText(buffer);
    }

    @Override
    public void OnState() {

        ICSApi icsApi = (ICSApi)gameApi;
        int myTurn = icsApi.getMyTurn();
        int turn = icsApi.getTurn();
        _tvLastMove.setText(icsApi.getLastMove());
        _tvPlayerTop.setText(icsApi.getOpponentPlayerName(myTurn));
        _tvPlayerBottom.setText(icsApi.getMyPlayerName(myTurn));

        int lastTo = icsApi.getLastTo();
        highlightedPositions.clear();
        if (lastTo != -1) {
            highlightedPositions.add(lastTo);
        }

        chessBoardView.setRotated(myTurn == BoardConstants.BLACK);

        localClockApi.startClock(icsApi.getIncrement(), icsApi.getWhiteRemaining(), icsApi.getBlackRemaining(), icsApi.getTurn(), System.currentTimeMillis());

        switchTurnOpp.setVisibility(turn == BoardConstants.BLACK && myTurn == BoardConstants.WHITE || turn == BoardConstants.WHITE && myTurn == BoardConstants.BLACK ?  View.VISIBLE : View.INVISIBLE);
        switchTurnOpp.setDisplayedChild(turn == BoardConstants.BLACK ? 0 : 1);

        switchTurnMe.setVisibility(turn == BoardConstants.WHITE && myTurn == BoardConstants.WHITE || turn == BoardConstants.BLACK && myTurn == BoardConstants.BLACK ?  View.VISIBLE : View.INVISIBLE);
        switchTurnMe.setDisplayedChild(turn == BoardConstants.BLACK ? 0 : 1);

        if (myTurn == turn && hasPremoved()) {
            requestMove(premoveFrom, premoveTo);
            resetPremove();
        }

        super.OnState();
    }

    @Override
    public void OnClockTime() {
        int myTurn = ((ICSApi) gameApi).getMyTurn();
        _tvClockTop.setText(myTurn == BoardConstants.WHITE ? localClockApi.getBlackRemainingTime() : localClockApi.getWhiteRemainingTime());
        _tvClockBottom.setText(myTurn == BoardConstants.BLACK ? localClockApi.getBlackRemainingTime() : localClockApi.getWhiteRemainingTime());

        if (isPlaying) {
            long remaining = myTurn == BoardConstants.WHITE ? localClockApi.getWhiteRemaining() : localClockApi.getBlackRemaining();
            if (remaining < _TimeWarning * 1000) {
                _tvClockBottom.setBackgroundColor(0xCCFF0000);
            } else {
                _tvClockBottom.setBackgroundColor(Color.TRANSPARENT);
            }

            if (_bTimeWarning && spSound != null) {
                spSound.play(soundTickTock, fVolume, fVolume, 1, 0, 1);
            }
        }
    }

    protected void addConsoleText(String s) {
        final String s2 = _tvConsole.getText() + "\n\n" + s;
        if (s2.length() > 8192) {
            _tvConsole.setText(s2.substring(s2.length() - 4096));
        } else {
            _tvConsole.append("\n\n" + s);
        }

        _scrollConsole.post(new Runnable() {//
            public void run() {
                _scrollConsole.fullScroll(HorizontalScrollView.FOCUS_DOWN);
            }
        });
    }
}

