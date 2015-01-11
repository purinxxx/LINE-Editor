package biz.dnote.lineeditor;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.http.protocol.HTTP;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.EditText;
import android.content.Intent;
import android.net.Uri;


public class MyActivity extends Activity {

    private static final String[] WORDS = { "同一人物に複数招待されているグループ削除",
            "招待されているグループ削除",
            "参加しているグループ削除",
            "画像ありグループ削除",
            "画像なしグループ削除",
            "招待されている画像ありグループ削除",
            "招待されている画像なしグループ削除",
            "参加している画像ありグループ削除",
            "参加している画像なしグループ削除",
            "トーク履歴全削除",
            "見たことある人を知り合いかもにする",
            "オン　招待ブロック（全て）",
            "オフ　招待ブロック（全て）",
            "オン　招待ブロック（同一人物複数）",
            "オフ　招待ブロック（同一人物複数）" };
    private static final String[] SQLCMD = { "DELETE FROM groups WHERE status=1 and creator=(SELECT creator FROM groups GROUP BY creator HAVING count(creator)>1);",
            "DELETE FROM groups WHERE status= 1;",
            "DELETE FROM groups WHERE status= 0;",
            "DELETE FROM groups WHERE picture_status is NOT NULL;",
            "DELETE FROM groups WHERE picture_status is NULL;",
            "DELETE FROM groups WHERE status= 1 and picture_status is NOT NULL;",
            "DELETE FROM groups WHERE status= 1 and picture_status is NULL;",
            "DELETE FROM groups WHERE status= 0 amd picture_status is NOT NULL;",
            "DELETE FROM groups WHERE status= 0 and picture_status is NULL;",
            "DELETE FROM chat_history;\n" + "DELETE FROM chat;",
            "UPDATE contacts SET relation=0, status=1 WHERE relation=2 and status=0;",
            "CREATE TRIGGER invitation INSERT ON groups\n" + "BEGIN\n" + "DELETE FROM groups WHERE status= 1;\n" + "END;",
            "DROP TRIGGER invitation;",
            "CREATE TRIGGER invitation2 INSERT ON groups\n" + "BEGIN\n" + "DELETE FROM groups WHERE status=1 and creator=(SELECT creator FROM groups GROUP BY creator HAVING count(creator)>1);\n" + "END;",
            "DROP TRIGGER invitation2;" };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, WORDS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                EditText editText = (EditText) findViewById(R.id.editText);
                editText.setText(SQLCMD[position]);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.about) {
            String url = "http://dnote.biz/line-editor";
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }




    Process _process = null;        //suプロセス
    DataOutputStream _outputStream = null;  //出力ストリーム
    DataInputStream _inputStream = null;  //入力ストリーム


    public boolean init(){
        boolean ret = false;

        try {
            //su実行
            _process = Runtime.getRuntime().exec("su");

            //入出力ストリーム取得
            _outputStream = new DataOutputStream(_process.getOutputStream());
            _inputStream = new DataInputStream(_process.getInputStream());

            //バージョンを取得する
            if(!write("su -v\n")){
            }else{
                String[] results = read().split("\n");
                for (String line : results) {
                    if(line.length() > 0){
                        //バージョンがとれたので成功
                        ret = true;
                    }
                }
            }
        } catch (IOException e) {
        }

        return ret;
    }

    public void deinit(){
        if(_inputStream != null){
            try {
                _inputStream.close();
            } catch (IOException e) {
            }
        }
        if(_outputStream != null){
            try {
                if(_process != null){
                    //プロセスと出力ストリームがある場合は
                    //シェルを終了する
                    _outputStream.writeBytes("exit\n");
                    _outputStream.flush();
                    try {
                        //シェルの終了を待つ
                        _process.waitFor();
                    } catch (InterruptedException e) {
                    }
                }
                _outputStream.close();
            } catch (IOException e) {
            }
        }

        if(_process != null){
            _process.destroy();
        }

        _outputStream = null;
        _inputStream = null;
        _process = null;
    }

    /**
     * コマンドをsuシェルへ投げる
     * 最後に\nを付けるまで実行されない
     * @param command
     */
    public boolean write(String command){
        boolean ret = false;
        if(_outputStream == null){
        }else{
            try {
                _outputStream.writeBytes(command);
                _outputStream.flush();
                ret = true;
            } catch (IOException e) {
            }
        }
        return ret;
    }

    /**
     * suシェルの出力を文字列にする
     * かならず結果が戻ってくる時に使う
     * それ以外で使うともどってこなくなるので注意
     * もちろん結果は標準出力でエラー出力はダメ
     * コマンドの結果が複数行になる場合は
     * splitなどを使ってバラして使う
     * @return
     */
    public String read(){
        String ret = "";

        if(_inputStream == null){
        }else{
            int size = 0;
            byte[] buffer = new byte[1024];

            try {
                do {
                    size = _inputStream.read(buffer);
                    if(size > 0){
                        ret += new String(buffer, 0, size, HTTP.UTF_8);
                    }
                }while(_inputStream.available() > 0);
            } catch (IOException e) {
            }
        }

        return ret;
    }


    public void onClickButton(View view) throws InterruptedException {
        EditText editText = (EditText)findViewById(R.id.editText);
        init();
        write("cd /data/data/jp.naver.line.android/databases\n");
        write("sqlite3 naver_line\n");
        write(editText.getText().toString() + "\n");
    }
}

