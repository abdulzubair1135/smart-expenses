package com.smarttracker.ui.voice;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.smarttracker.R;
import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.AppDatabase;
import com.smarttracker.data.local.entity.AccountEntity;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.BottomSheetVoiceAssistantBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Bottom Sheet Dialog for Smart Expenses Tracker's Voice Assistant.
 * Operates 100% locally on the device using the built-in deterministic language parser.
 */
public class VoiceAssistantBottomSheetDialog extends BottomSheetDialog {

    private static final String TAG = "VoiceAssistantDialog";

    public interface OnTransactionSavedListener {
        void onTransactionSaved();
    }

    public interface OnEditDraftListener {
        void onEditDraft(VoiceCommandModel draft);
    }

    private final Activity activity;
    private final TransactionRepository repository;
    private final SessionManager sessionManager;
    private BottomSheetVoiceAssistantBinding binding;

    private SpeechRecognitionManager speechManager;
    private TextToSpeechManager ttsManager;

    private VoiceCommandModel currentDraft;
    private boolean isSaving = false;
    private OnTransactionSavedListener onTransactionSavedListener;
    private OnEditDraftListener onEditDraftListener;

    public VoiceAssistantBottomSheetDialog(@NonNull Activity activity, TransactionRepository repository) {
        super(activity, R.style.Theme_SmartExpensesTracker);
        this.activity = activity;
        this.repository = repository;
        this.sessionManager = SessionManager.getInstance(activity);
    }

    public void setOnTransactionSavedListener(OnTransactionSavedListener listener) {
        this.onTransactionSavedListener = listener;
    }

    public void setOnEditDraftListener(OnEditDraftListener listener) {
        this.onEditDraftListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = BottomSheetVoiceAssistantBinding.inflate(LayoutInflater.from(getContext()));
        setContentView(binding.getRoot());

        if (getBehavior() != null) {
            getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            getBehavior().setSkipCollapsed(true);
        }

        ttsManager = new TextToSpeechManager(getContext());
        initSpeechRecognizer();

        // Update engine badge
        if (binding != null) {
            binding.tvEngineBadge.setText("⚡ Built-in Voice Engine (Offline)");
        }

        binding.btnCloseVoice.setOnClickListener(v -> dismiss());
        binding.cardMicButton.setOnClickListener(v -> toggleListening());

        // Mute toggle
        binding.btnMuteVoice.setOnClickListener(v -> {
            boolean newMuted = !ttsManager.isMuted();
            ttsManager.setMuted(newMuted);
            binding.btnMuteVoice.setImageTintList(ColorStateList.valueOf(newMuted ? 0xFF94A3B8 : 0xFF10B981));
            Toast.makeText(getContext(), newMuted ? "Voice response muted" : "Voice response unmuted", Toast.LENGTH_SHORT).show();
        });

        // Manual text entry handlers (Offline fallback)
        binding.btnSendManual.setOnClickListener(v -> submitManualInput());
        binding.etManualInput.setOnEditorActionListener((v, actionId, event) -> {
            submitManualInput();
            return true;
        });

        // Confirmation and Action Buttons
        binding.btnConfirmSave.setOnClickListener(v -> confirmAndSaveDraft());
        binding.btnEditDraft.setOnClickListener(v -> editDraft());
        binding.btnCancelDraft.setOnClickListener(v -> cancelDraft());

        // Undo Confirmation Buttons
        binding.btnConfirmUndo.setOnClickListener(v -> executeConfirmedUndo());
        binding.btnCancelUndo.setOnClickListener(v -> cancelUndo());

        // Suggestion Chips
        binding.chipSampleExpense.setOnClickListener(v -> handleSpeechResult("Aaj lunch mein 250 rs kharch hue UPI se"));
        binding.chipSampleBalance.setOnClickListener(v -> handleSpeechResult("Mera total balance kitna hai"));
        binding.chipSampleLend.setOnClickListener(v -> handleSpeechResult("Rohan ko 500 rupaye udhar diye"));
        binding.chipSampleReceived.setOnClickListener(v -> handleSpeechResult("Papa se 2000 rupaye mile"));
        binding.chipSampleToday.setOnClickListener(v -> handleSpeechResult("Aaj kitna kharch hua"));
        binding.chipSampleSplit.setOnClickListener(v -> handleSpeechResult("1200 ka bill 4 logo me split karo"));
        binding.chipSampleUndo.setOnClickListener(v -> handleSpeechResult("Aakhri transaction delete kar do"));

        setOnDismissListener(dialog -> cleanup());

        // Auto-listen on entry if permission granted
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (speechManager != null && speechManager.hasPermission()) {
                startListening();
            } else {
                requestAudioPermission();
            }
        }, 300);
    }

    private void submitManualInput() {
        if (binding.etManualInput.getText() == null) return;
        String text = binding.etManualInput.getText().toString().trim();
        if (!text.isEmpty()) {
            binding.etManualInput.setText("");
            handleSpeechResult(text);
        }
    }

    private void initSpeechRecognizer() {
        speechManager = new SpeechRecognitionManager(activity, new SpeechRecognitionManager.SpeechCallback() {
            @Override
            public void onReadyForSpeech() {
                activity.runOnUiThread(() -> {
                    binding.tvVoiceStatus.setText("Listening... (Abhi boliye, sun raha hoon)");
                    binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.brand_primary));
                    binding.cardMicButton.setStrokeColor(ContextCompat.getColor(getContext(), R.color.financial_expense));
                    binding.cardMicButton.setCardBackgroundColor(ColorStateList.valueOf(0xFFFEE2E2));
                    binding.ivMicIcon.setImageTintList(ColorStateList.valueOf(0xFFEF4444));
                });
            }

            @Override
            public void onBeginningOfSpeech() {
                activity.runOnUiThread(() -> {
                    binding.tvVoiceStatus.setText("Awaz aa rahi hai, sun raha hoon...");
                });
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Audio level visualization
            }

            @Override
            public void onPartialResults(String partialTranscript) {
                activity.runOnUiThread(() -> {
                    binding.tvVoiceStatus.setText("Sun raha hoon: “" + partialTranscript + "…”");
                    binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.brand_primary));
                });
            }

            @Override
            public void onResults(String transcript) {
                activity.runOnUiThread(() -> handleSpeechResult(transcript));
            }

            @Override
            public void onError(String errorMessage) {
                activity.runOnUiThread(() -> {
                    resetMicVisuals();
                    binding.tvVoiceStatus.setText(errorMessage);
                    binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
                });
            }
        });
    }

    private void toggleListening() {
        if (speechManager == null) return;
        if (speechManager.isListening()) {
            stopListening();
        } else {
            if (speechManager.hasPermission()) {
                startListening();
            } else {
                requestAudioPermission();
            }
        }
    }

    private void startListening() {
        if (speechManager != null) {
            speechManager.startListening();
        }
    }

    private void stopListening() {
        if (speechManager != null) {
            speechManager.stopListening();
        }
        resetMicVisuals();
    }

    private void resetMicVisuals() {
        if (binding == null) return;
        binding.tvVoiceStatus.setText("Tap microphone to speak");
        binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        binding.cardMicButton.setStrokeColor(ContextCompat.getColor(getContext(), R.color.brand_primary));
        binding.cardMicButton.setCardBackgroundColor(ColorStateList.valueOf(0xFFFFFFFF));
        binding.ivMicIcon.setImageTintList(ColorStateList.valueOf(0xFF10B981));
    }

    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(activity, new String[]{android.Manifest.permission.RECORD_AUDIO}, 2001);
    }

    private TransactionEntity pendingUndoTx = null;

    private boolean isAffirmative(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\b(haa|ha|haan|yes|yep|confirm|save|kardo|kar do|karo|theek hai|sahi hai|ok|done|rec|record|haanji|haan ji|kar do save|kardo save|ha kardo|haa kardo|haan kardo|kar do record|kardo record|kardo rec|kar do rec|sev|sev kar do|सेव|कर दो|करदो|हां|हाँ|हॉ|हांजी|ठीक है|सही है)\\b.*");
    }

    private boolean isNegative(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\b(nahi|na|nahin|no|cancel|mat karo|mat kar|mat kardo|mat kar do|rehne do|rehne de|cancel kar do|hatao|hata do|discard|stop|chhod do|cancel karo|मत|नहीं|ना|रहने दो|कैंसिल|हटाओ)\\b.*");
    }

    private void handleSpeechResult(String transcript) {
        resetMicVisuals();
        binding.cardTranscript.setVisibility(View.VISIBLE);
        binding.tvTranscript.setText("“" + transcript + "”");

        // 1. If currently waiting for Undo confirmation:
        if (binding.cardUndoConfirmation.getVisibility() == View.VISIBLE && pendingUndoTx != null) {
            if (isAffirmative(transcript)) {
                executeConfirmedUndo();
                return;
            } else if (isNegative(transcript)) {
                cancelUndo();
                return;
            } else {
                ttsManager.speak("Delete karne ke liye 'Haa' bole ya 'Nahi' bole.");
                return;
            }
        }

        // 2. If currently reviewing a Transaction Draft (cardVoiceDraft is VISIBLE):
        if (binding.cardVoiceDraft.getVisibility() == View.VISIBLE && currentDraft != null && currentDraft.amountMinor > 0) {
            if (isAffirmative(transcript)) {
                confirmAndSaveDraft();
                return;
            } else if (isNegative(transcript)) {
                cancelDraft();
                return;
            }
        }

        binding.tvVoiceStatus.setText("Processing voice command...");
        binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.brand_primary));

        // Directly parse on-device using default deterministic engine (< 1ms, zero latency)
        VoiceCommandModel result = LocalPatternLanguageParser.parse(transcript);
        processParsedCommand(result);
    }

    private void processParsedCommand(VoiceCommandModel result) {
        if ("CONFIRM_DRAFT".equals(result.intent)) {
            if (this.currentDraft != null && this.currentDraft.amountMinor > 0) {
                confirmAndSaveDraft();
            } else {
                ttsManager.speak("Pehle koi transaction boliye, tab save karenge.");
            }
            return;
        }

        if ("CANCEL_DRAFT".equals(result.intent)) {
            cancelDraft();
            return;
        }

        if ("UNDO_LAST_TRANSACTION".equals(result.intent)) {
            handleUndoLastTransaction();
            return;
        }

        if (result.intent != null && result.intent.startsWith("QUERY_")) {
            handleFinancialQuery(result);
            return;
        }

        this.currentDraft = result;

        if (result.clarificationRequired) {
            binding.cardVoiceDraft.setVisibility(View.GONE);
            binding.cardQueryResult.setVisibility(View.GONE);
            binding.cardUndoConfirmation.setVisibility(View.GONE);
            binding.cardClarification.setVisibility(View.VISIBLE);
            binding.tvClarificationQuestion.setText(result.clarificationQuestion != null ? result.clarificationQuestion : "Kripya clear boliye.");
            binding.tvVoiceStatus.setText("Clarification required");
            binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.financial_expense));
            if (result.spokenResponse != null && !result.spokenResponse.isEmpty()) {
                ttsManager.speak(result.spokenResponse);
            }
            return;
        }

        // Render Transaction Draft Card
        renderDraftCard(result);
    }

    private void handleUndoLastTransaction() {
        stopListening();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String userId = sessionManager.getUserId();
            TransactionEntity lastTx = AppDatabase.getDatabase(getContext()).transactionDao().getLatestTransaction(userId);
            if (lastTx != null) {
                pendingUndoTx = lastTx;
                long rupees = lastTx.amountMinor / 100;
                String typeHindi = "expense".equalsIgnoreCase(lastTx.type) ? "kharch" :
                        "received".equalsIgnoreCase(lastTx.type) ? "kamai" :
                        "lend".equalsIgnoreCase(lastTx.type) ? "udhaar diya" :
                        "borrow".equalsIgnoreCase(lastTx.type) ? "udhaar liya" : lastTx.type;

                String spokenMsg = "Kya aap " + rupees + " rupaye ka " + typeHindi + " delete karna chahte hain? Confirm karne ke liye 'Haa' bole ya button dabaye.";

                activity.runOnUiThread(() -> {
                    resetMicVisuals();
                    if (getBehavior() != null) {
                        getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
                    }

                    binding.cardClarification.setVisibility(View.GONE);
                    binding.cardQueryResult.setVisibility(View.GONE);
                    binding.cardVoiceDraft.setVisibility(View.GONE);
                    binding.cardUndoConfirmation.setVisibility(View.VISIBLE);

                    binding.tvUndoAmount.setText(FinancialEngine.formatMinorToSimpleINR(lastTx.amountMinor));
                    binding.tvUndoQuestion.setText("Kya aap ₹" + rupees + " ka " + typeHindi + " delete / undo karna chahte hain?");
                    String desc = (lastTx.description != null && !lastTx.description.isEmpty()) ? lastTx.description : typeHindi;
                    binding.tvUndoDetails.setText(desc + " • " + typeHindi.toUpperCase());
                    binding.tvUndoDate.setText(lastTx.transactionDate != null ? lastTx.transactionDate.split("T")[0] : "Today");

                    binding.tvVoiceStatus.setText("Confirm Delete (" + FinancialEngine.formatMinorToSimpleINR(lastTx.amountMinor) + ")");
                    binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.financial_expense));

                    ttsManager.speak(spokenMsg, utteranceId -> {
                        activity.runOnUiThread(() -> {
                            if (binding != null && binding.cardUndoConfirmation.getVisibility() == View.VISIBLE) {
                                startListening();
                            }
                        });
                    });
                });
            } else {
                activity.runOnUiThread(() -> {
                    resetMicVisuals();
                    binding.tvVoiceStatus.setText("Delete karne ke liye koi transaction nahi mila");
                    ttsManager.speak("Delete karne ke liye koi transaction nahi mila.");
                });
            }
        });
    }

    private void executeConfirmedUndo() {
        if (pendingUndoTx == null) return;
        stopListening();
        TransactionEntity txToDelete = pendingUndoTx;
        pendingUndoTx = null;

        // Delete from local Room database AND notify Supabase cloud server
        repository.deleteTransaction(txToDelete.id);

        long rupees = txToDelete.amountMinor / 100;
        String typeHindi = "expense".equalsIgnoreCase(txToDelete.type) ? "kharch" :
                "received".equalsIgnoreCase(txToDelete.type) ? "kamai" :
                "lend".equalsIgnoreCase(txToDelete.type) ? "udhaar diya" : txToDelete.type;
        String successMsg = "Theek hai! Aapka " + rupees + " rupaye ka " + typeHindi + " delete kar diya gaya hai.";

        activity.runOnUiThread(() -> {
            resetMicVisuals();
            binding.cardUndoConfirmation.setVisibility(View.GONE);
            binding.cardVoiceDraft.setVisibility(View.GONE);
            binding.tvVoiceStatus.setText("Delete ho gaya! Agla boliye ya mic dabaye");
            binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.financial_expense));
            Toast.makeText(getContext(), "Deleted ₹" + rupees + " (" + txToDelete.type + ")", Toast.LENGTH_SHORT).show();

            if (onTransactionSavedListener != null) {
                onTransactionSavedListener.onTransactionSaved();
            }

            // Speak full sentence completely and DO NOT auto-dismiss to home screen
            ttsManager.speak(successMsg, utteranceId -> {
                activity.runOnUiThread(this::resetMicVisuals);
            });
        });
    }

    private void cancelUndo() {
        pendingUndoTx = null;
        stopListening();
        activity.runOnUiThread(() -> {
            resetMicVisuals();
            binding.cardUndoConfirmation.setVisibility(View.GONE);
            binding.cardVoiceDraft.setVisibility(View.GONE);
            binding.tvVoiceStatus.setText("Undo cancelled. Agla boliye ya mic dabaye");
            ttsManager.speak("Theek hai, cancel kar diya.", utteranceId -> {
                activity.runOnUiThread(this::resetMicVisuals);
            });
        });
    }

    private void renderDraftCard(VoiceCommandModel draft) {
        if (getBehavior() != null) {
            getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        }

        binding.cardClarification.setVisibility(View.GONE);
        binding.cardQueryResult.setVisibility(View.GONE);
        binding.cardUndoConfirmation.setVisibility(View.GONE);
        binding.cardVoiceDraft.setVisibility(View.VISIBLE);

        binding.tvVoiceStatus.setText("Review & Confirm Transaction");
        binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));

        if (draft.transactionType == null) {
            binding.cardVoiceDraft.setVisibility(View.GONE);
            binding.cardClarification.setVisibility(View.VISIBLE);
            binding.tvClarificationQuestion.setText("Ye transaction kis type ka hai: kharch, kamai, udhaar diya ya udhaar liya?");
            binding.tvVoiceStatus.setText("Clarification required");
            ttsManager.speak("Ye transaction kis type ka hai: kharch, kamai, udhaar diya ya udhaar liya?");
            return;
        }

        String typeDisplay = draft.transactionType.toUpperCase(Locale.ROOT);
        binding.tvDraftType.setText(typeDisplay);

        if ("expense".equalsIgnoreCase(draft.transactionType)) {
            binding.tvDraftType.setBackgroundTintList(ColorStateList.valueOf(0xFFFEE2E2));
            binding.tvDraftType.setTextColor(0xFFEF4444);
        } else if ("received".equalsIgnoreCase(draft.transactionType)) {
            binding.tvDraftType.setBackgroundTintList(ColorStateList.valueOf(0xFFDCFCE7));
            binding.tvDraftType.setTextColor(0xFF10B981);
        } else if ("lend".equalsIgnoreCase(draft.transactionType)) {
            binding.tvDraftType.setBackgroundTintList(ColorStateList.valueOf(0xFFFEF3C7));
            binding.tvDraftType.setTextColor(0xFFD97706);
        } else if ("borrow".equalsIgnoreCase(draft.transactionType)) {
            binding.tvDraftType.setBackgroundTintList(ColorStateList.valueOf(0xFFEDE9FE));
            binding.tvDraftType.setTextColor(0xFF7C3AED);
        } else {
            binding.tvDraftType.setBackgroundTintList(ColorStateList.valueOf(0xFFE0E7FF));
            binding.tvDraftType.setTextColor(0xFF4F46E5);
        }

        binding.tvDraftAmount.setText(FinancialEngine.formatMinorToSimpleINR(draft.amountMinor));
        binding.tvDraftCategory.setText(draft.categoryName != null ? draft.categoryName : "General");
        binding.tvDraftAccount.setText(draft.accountName != null ? draft.accountName : "Default Account");
        binding.tvDraftDate.setText(draft.transactionDate != null ? draft.transactionDate : "Today");
        binding.tvDraftDescription.setText("Note: " + (draft.description != null ? draft.description : "-"));

        // Speak confirmation prompt via offline TTS and auto-listen on finish
        if (draft.spokenResponse != null && !draft.spokenResponse.isEmpty()) {
            ttsManager.speak(draft.spokenResponse, utteranceId -> {
                activity.runOnUiThread(() -> {
                    // Activate listening so user can simply say "Haa kardo" / "Kardo" / "Nahi"
                    if (binding != null && binding.cardVoiceDraft.getVisibility() == View.VISIBLE) {
                        startListening();
                    }
                });
            });
        }
    }

    private void handleFinancialQuery(VoiceCommandModel query) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String userId = sessionManager.getUserId();
            long total = 0;
            String title = "Query Result";
            String spoken = "";

            if ("QUERY_BALANCE".equals(query.intent)) {
                title = "Available Balance";
                long base = 0;
                List<AccountEntity> accounts = AppDatabase.getDatabase(getContext()).accountDao().getAccounts(userId);
                for (AccountEntity acc : accounts) {
                    base += acc.openingBalanceMinor;
                }
                if (base == 0) base = 1650000;
                List<TransactionEntity> list = AppDatabase.getDatabase(getContext()).transactionDao().getAllTransactions(userId);
                long inc = 0, exp = 0;
                for (TransactionEntity tx : list) {
                    if ("received".equalsIgnoreCase(tx.type) || "borrow".equalsIgnoreCase(tx.type)) inc += tx.amountMinor;
                    else if ("expense".equalsIgnoreCase(tx.type) || "lend".equalsIgnoreCase(tx.type)) exp += tx.amountMinor;
                }
                total = base + inc - exp;
                spoken = "Aapka total balance " + (total / 100) + " rupaye hai.";
            } else if ("QUERY_TODAY_EXPENSE".equals(query.intent)) {
                title = "Today's Expense";
                String todayIso = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                List<TransactionEntity> list = AppDatabase.getDatabase(getContext()).transactionDao().getAllTransactions(userId);
                for (TransactionEntity tx : list) {
                    if (tx.transactionDate != null && tx.transactionDate.startsWith(todayIso)) {
                        if ("expense".equalsIgnoreCase(tx.type) || "lend".equalsIgnoreCase(tx.type)) {
                            total += tx.amountMinor;
                        }
                    }
                }
                spoken = "Aaj aapka kul kharch " + (total / 100) + " rupaye hua hai.";
            } else if ("QUERY_DEBT_LENT".equals(query.intent)) {
                title = "Lent to Friends (Udhaar Diya)";
                List<TransactionEntity> list = AppDatabase.getDatabase(getContext()).transactionDao().getAllTransactions(userId);
                for (TransactionEntity tx : list) {
                    if ("lend".equalsIgnoreCase(tx.type)) {
                        total += tx.amountMinor;
                    }
                }
                spoken = "Dosto se aapko kul " + (total / 100) + " rupaye lene hain.";
            } else if ("QUERY_DEBT_BORROWED".equals(query.intent)) {
                title = "Borrowed from Friends (Udhaar Liya)";
                List<TransactionEntity> list = AppDatabase.getDatabase(getContext()).transactionDao().getAllTransactions(userId);
                for (TransactionEntity tx : list) {
                    if ("borrow".equalsIgnoreCase(tx.type)) {
                        total += tx.amountMinor;
                    }
                }
                spoken = "Dosto ko aapko kul " + (total / 100) + " rupaye dene hain.";
            } else if ("QUERY_CATEGORY_TOTAL".equals(query.intent)) {
                String cat = query.categoryName != null ? query.categoryName : "Food";
                title = cat + " Total Expense";
                String targetCatId = "cat_" + cat.toLowerCase(Locale.ROOT);
                List<TransactionEntity> list = AppDatabase.getDatabase(getContext()).transactionDao().getAllTransactions(userId);
                for (TransactionEntity tx : list) {
                    if (targetCatId.equalsIgnoreCase(tx.categoryId) || (tx.description != null && tx.description.toLowerCase(Locale.ROOT).contains(cat.toLowerCase(Locale.ROOT)))) {
                        if ("expense".equalsIgnoreCase(tx.type)) {
                            total += tx.amountMinor;
                        }
                    }
                }
                spoken = cat + " category mein aapka kul kharch " + (total / 100) + " rupaye hua hai.";
            } else if ("QUERY_EXPENSES".equals(query.intent)) {
                title = "Total Expense";
                List<TransactionEntity> list = AppDatabase.getDatabase(getContext()).transactionDao().getAllTransactions(userId);
                for (TransactionEntity tx : list) {
                    if ("expense".equalsIgnoreCase(tx.type) || "lend".equalsIgnoreCase(tx.type)) total += tx.amountMinor;
                }
                spoken = "Aapka kul kharch " + (total / 100) + " rupaye hai.";
            }

            final long finalTotal = total;
            final String finalTitle = title;
            final String finalSpoken = spoken;

            activity.runOnUiThread(() -> {
                binding.cardClarification.setVisibility(View.GONE);
                binding.cardVoiceDraft.setVisibility(View.GONE);
                binding.cardQueryResult.setVisibility(View.VISIBLE);

                binding.tvQueryTitle.setText(finalTitle);
                binding.tvQueryAmount.setText(FinancialEngine.formatMinorToSimpleINR(finalTotal));
                binding.tvVoiceStatus.setText("Offline Query Answered");
                binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.brand_primary));

                ttsManager.speak(finalSpoken);
            });
        });
    }

    private void confirmAndSaveDraft() {
        if (isSaving || currentDraft == null || currentDraft.amountMinor <= 0 || currentDraft.transactionType == null) {
            return;
        }

        String type = currentDraft.transactionType.toLowerCase(Locale.ROOT);
        if (!"expense".equals(type) && !"received".equals(type) && !"lend".equals(type) && !"borrow".equals(type) && !"transfer".equals(type)) {
            ttsManager.speak("Transaction type invalid hai.");
            return;
        }

        isSaving = true;

        String userId = sessionManager.getUserId();
        String txId = UUID.randomUUID().toString();

        // Map category and account to seeded IDs
        String categoryId = "cat_other";
        if ("Food".equalsIgnoreCase(currentDraft.categoryName)) categoryId = "cat_food";
        else if ("Travel".equalsIgnoreCase(currentDraft.categoryName)) categoryId = "cat_travel";
        else if ("Shopping".equalsIgnoreCase(currentDraft.categoryName)) categoryId = "cat_shopping";
        else if ("Bills".equalsIgnoreCase(currentDraft.categoryName)) categoryId = "cat_bills";
        else if ("Salary".equalsIgnoreCase(currentDraft.categoryName)) categoryId = "cat_salary";

        String accountId = "acc_cash";
        if ("UPI".equalsIgnoreCase(currentDraft.accountName)) accountId = "acc_upi";
        else if ("Bank".equalsIgnoreCase(currentDraft.accountName)) accountId = "acc_bank";
        else if ("Savings".equalsIgnoreCase(currentDraft.accountName)) accountId = "acc_savings";

        String isoDate = currentDraft.transactionDate != null
                ? currentDraft.transactionDate + "T12:00:00.000Z"
                : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date());

        TransactionEntity entity = new TransactionEntity(
                txId,
                userId,
                accountId,
                type,
                currentDraft.amountMinor,
                "INR",
                categoryId,
                currentDraft.description != null ? currentDraft.description : "Voice " + type,
                isoDate,
                "completed",
                currentDraft.idempotencyKey != null ? currentDraft.idempotencyKey : UUID.randomUUID().toString(),
                currentDraft.recipient,
                currentDraft.destinationAccount,
                false,
                System.currentTimeMillis()
        );

        // Save directly to Room local storage (100% offline)
        repository.insertTransaction(entity);
        try {
            com.smarttracker.core.sync.SyncManager.getInstance(getContext()).triggerSync();
        } catch (Throwable ignored) {}

        // Send local push notification for transaction confirmation
        try {
            android.app.NotificationManager notifManager = (android.app.NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (notifManager != null) {
                String channelId = "smart_expenses_periodic_reminders";
                long inrRupees = currentDraft.amountMinor / 100;
                String notifMsg = "₹" + inrRupees + " (" + type.toUpperCase() + ") note ho gaya! Kharcha/Kamai history update ho gayi.";
                androidx.core.app.NotificationCompat.Builder notifBuilder = new androidx.core.app.NotificationCompat.Builder(getContext(), channelId)
                        .setSmallIcon(R.drawable.ic_notification_logo)
                        .setColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.brand_primary))
                        .setContentTitle("Transaction Saved ✅")
                        .setContentText(notifMsg)
                        .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(notifMsg))
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);
                notifManager.notify((int) System.currentTimeMillis(), notifBuilder.build());
            }
        } catch (Throwable ignored) {}

        long rupees = currentDraft.amountMinor / 100;
        String typeHindi = "expense".equalsIgnoreCase(type) ? "kharch" :
                "received".equalsIgnoreCase(type) ? "kamai" :
                "lend".equalsIgnoreCase(type) ? "udhaar diya" :
                "borrow".equalsIgnoreCase(type) ? "udhaar liya" : type;

        String successMsg = "Ho gaya! Aapka " + rupees + " rupaye ka " + typeHindi + " save ho gaya.";

        long savedAmountMinor = currentDraft.amountMinor;
        String savedType = type;

        activity.runOnUiThread(() -> {
            resetMicVisuals();
            binding.cardVoiceDraft.setVisibility(View.GONE);
            binding.cardClarification.setVisibility(View.GONE);
            binding.cardQueryResult.setVisibility(View.GONE);
            binding.cardUndoConfirmation.setVisibility(View.GONE);

            binding.tvVoiceStatus.setText("Save ho gaya (" + FinancialEngine.formatMinorToSimpleINR(savedAmountMinor) + ")! Agla boliye ya mic dabaye");
            binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.brand_primary));
            Toast.makeText(getContext(), "Saved " + FinancialEngine.formatMinorToSimpleINR(savedAmountMinor) + " (" + savedType.toUpperCase() + ")", Toast.LENGTH_SHORT).show();

            currentDraft = null;
            isSaving = false;

            if (onTransactionSavedListener != null) {
                onTransactionSavedListener.onTransactionSaved();
            }

            // Speak full sentence completely and DO NOT auto-dismiss to home screen
            ttsManager.speak(successMsg, utteranceId -> {
                activity.runOnUiThread(this::resetMicVisuals);
            });
        });
    }

    private void editDraft() {
        if (currentDraft != null && onEditDraftListener != null) {
            onEditDraftListener.onEditDraft(currentDraft);
        }
        dismiss();
    }

    private void cancelDraft() {
        currentDraft = null;
        isSaving = false;
        activity.runOnUiThread(() -> {
            resetMicVisuals();
            binding.cardVoiceDraft.setVisibility(View.GONE);
            binding.cardClarification.setVisibility(View.GONE);
            binding.tvVoiceStatus.setText("Cancel ho gaya. Agla boliye ya mic dabaye");
            ttsManager.speak("Theek hai, cancel kar diya.", utteranceId -> {
                activity.runOnUiThread(this::resetMicVisuals);
            });
        });
    }

    private void cleanup() {
        if (speechManager != null) {
            speechManager.destroy();
            speechManager = null;
        }
        if (ttsManager != null) {
            ttsManager.shutdown();
            ttsManager = null;
        }
    }
}
