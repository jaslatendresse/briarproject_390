package org.briarproject.briar.android.login;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import org.briarproject.bramble.plugin.tcp.UniqueIDSingleton;
import org.briarproject.bramble.restClient.BServerServicesImpl;
import org.briarproject.bramble.restClient.ServerObj.AllArticles;
import org.briarproject.bramble.restClient.ServerObj.PwdSingletonServer;
import org.briarproject.bramble.restClient.ServerObj.SavedUser;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.controller.BriarController;
import org.briarproject.briar.android.controller.handler.UiResultHandler;
import org.briarproject.briar.android.util.UiUtils;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class PasswordActivity extends BaseActivity {

	private static final Logger LOG =
			Logger.getLogger(PasswordActivity.class.getName());
	@Inject
	PasswordController passwordController;

	@Inject
	BriarController briarController;

	private Button signInButton;
	private ProgressBar progress;
	private TextInputLayout input;
	private EditText password;
	private volatile boolean creationCompleted;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		// fade-in after splash screen instead of default animation
		overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

		if (!passwordController.accountExists()) {
			deleteAccount();
			return;
		}

		setContentView(R.layout.activity_password);
		signInButton = findViewById(R.id.btn_sign_in);
		progress = findViewById(R.id.progress_wheel);
		input = findViewById(R.id.password_layout);
		password = findViewById(R.id.edit_password);
		password.setOnEditorActionListener((v, actionId, event) -> {
			validatePassword();
			return true;
		});
		password.addTextChangedListener(new TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (count > 0) UiUtils.setError(input, null, false);
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
	}

	@Override
	public void onStart() {
		super.onStart();
		// If the user has already signed in, clean up this instance
		if (briarController.hasEncryptionKey()) {
			setResult(RESULT_OK);
			finish();
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onBackPressed() {
		// Show the home screen rather than another password prompt
		Intent intent = new Intent(ACTION_MAIN);
		intent.addCategory(CATEGORY_HOME);
		startActivity(intent);
	}

	private void deleteAccount() {
		passwordController.deleteAccount(this);
		setResult(RESULT_CANCELED);
		Intent i = new Intent(this, SetupActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(i);
	}

	public void onSignInClick(View v) {
	    BServerServicesImpl webServer = new BServerServicesImpl();

	    if(webServer.getOrUpdateAllArticles()) {
            AllArticles.getInstanceAllArticles();
        }

		validatePassword();
	}

	public void onForgottenPasswordClick(View v) {
		// TODO Encapsulate the dialog in a re-usable fragment
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
				R.style.BriarDialogTheme);
		builder.setTitle(R.string.dialog_title_lost_password);
		builder.setMessage(R.string.dialog_message_lost_password);
		builder.setPositiveButton(R.string.cancel, null);
		builder.setNegativeButton(R.string.delete,
				(dialog, which) -> deleteAccount());
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void validatePassword() {
		hideSoftKeyboard(password);
		signInButton.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		passwordController.validatePassword(password.getText().toString(),
				new UiResultHandler<Boolean>(this) {
					@Override
					public void onResultUi(@NonNull Boolean result) {
						if (result) {
							setResult(RESULT_OK);
                            PwdSingletonServer.setPassword(password.getText().toString());
                            new CallServerAsync().execute();
							supportFinishAfterTransition();
							// don't show closing animation,
							// but one for opening NavDrawerActivity
							overridePendingTransition(R.anim.screen_new_in,
									R.anim.screen_old_out);
						} else {
							tryAgain();
						}
					}
				});
		if(creationCompleted){
			LOG.info("Late user creation success");
		}
	}

	private void tryAgain() {
		UiUtils.setError(input, getString(R.string.try_again), true);
		signInButton.setVisibility(VISIBLE);
		progress.setVisibility(INVISIBLE);
		password.setText("");

		// show the keyboard again
		showSoftKeyboard(password);
	}

	/**
	 * This class is implementing an Async task as recommended for Android
	 * It is made to make sure to separate server call from main UI Thread
	 */
	class CallServerAsync extends AsyncTask<Void, Integer, String> {

		boolean resultFromCreateAccount;

		@Override
		protected String doInBackground(Void... voids) {
			BServerServicesImpl services = new BServerServicesImpl();
			if(UniqueIDSingleton.getUniqueID() != null){
				boolean doesUserEx = services.doesUsernameExistsInDB(UniqueIDSingleton.getUniqueID());
				if(!doesUserEx){
					// Let's add it to server...
					SavedUser placeHolderUser = new SavedUser(UniqueIDSingleton.getUniqueID(), "123.123.123.123", 1234, 1, 99);
					resultFromCreateAccount = services.createNewUser(placeHolderUser, PwdSingletonServer.getPassword());
				}
			}

			return null;
		}

		protected void onPostExecute(String result) {
			creationCompleted = resultFromCreateAccount;
		}
	}
}
