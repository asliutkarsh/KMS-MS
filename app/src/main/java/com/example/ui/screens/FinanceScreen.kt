package com.example.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.FinanceAccountEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.model.AccountType
import com.example.data.model.TransactionType
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatFullDateTime
import com.example.ui.components.formatShortTime
import com.example.ui.theme.LocalKmsSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.KmsMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    viewModel: KmsMainViewModel,
    onOpenAccountLedger: (String) -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val financeState by viewModel.financeState.collectAsStateWithLifecycle()
    val unlocked by viewModel.financeUnlocked.collectAsStateWithLifecycle()

    val isLocked = prefs.financeLockEnabled && !unlocked
    val showBalance = prefs.showBalance
    val semantic = LocalKmsSemanticColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Finance", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "On-device account & card parser",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (!isLocked) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(
                                if (showBalance) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Show",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Switch(
                                checked = showBalance,
                                onCheckedChange = { viewModel.toggleShowBalance() },
                                modifier = Modifier.testTag("finance_show_balance_toggle")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isLocked) {
            FinanceLockGate(
                hasPinConfigured = prefs.financePinHash.isNotBlank(),
                onBiometricSuccess = { viewModel.unlockFinanceVault() },
                onVerifyPin = { pin, cb -> viewModel.verifyPinAndUnlock(pin, cb) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else if (financeState.accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                EmptyStatePanel(
                    icon = Icons.Rounded.AccountBalance,
                    title = "No accounts detected yet",
                    subtitle = "Bank, credit/debit card, UPI wallet, and EPFO SMS alerts are parsed automatically on-device—no bank logins or internet required."
                )
            }
        } else {
            val grouped = remember(financeState.accounts) {
                financeState.accounts.groupBy { it.accountType }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 30-day Cashflow Overview Card
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "LAST 30 DAYS CASHFLOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = semantic.debitCoralContainer,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.ArrowUpward, null, tint = semantic.debitCoral, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Spent", style = MaterialTheme.typography.labelMedium, color = semantic.debitCoral)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = formatCurrency(
                                                financeState.monthlyDebitTotal,
                                                financeState.primaryCurrency,
                                                masked = !showBalance
                                            ),
                                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                                            color = semantic.debitCoral
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = semantic.creditGreenContainer,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.ArrowDownward, null, tint = semantic.creditGreen, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Received", style = MaterialTheme.typography.labelMedium, color = semantic.creditGreen)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = formatCurrency(
                                                financeState.monthlyCreditTotal,
                                                financeState.primaryCurrency,
                                                masked = !showBalance
                                            ),
                                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                                            color = semantic.creditGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Grouped Account Sections
                AccountType.entries.forEach { type ->
                    val list = grouped[type].orEmpty()
                    if (list.isNotEmpty()) {
                        item {
                            Text(
                                text = type.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                            )
                        }
                        items(list, key = { it.accountKey }) { account ->
                            AccountCardRow(
                                account = account,
                                showBalance = showBalance,
                                onClick = { onOpenAccountLedger(account.accountKey) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCardRow(
    account: FinanceAccountEntity,
    showBalance: Boolean,
    onClick: () -> Unit
) {
    val semantic = LocalKmsSemanticColors.current
    val icon = when (account.accountType) {
        AccountType.BANK -> Icons.Rounded.AccountBalance
        AccountType.CREDIT_CARD, AccountType.DEBIT_CARD -> Icons.Rounded.CreditCard
        AccountType.WALLET -> Icons.Rounded.AccountBalanceWallet
        AccountType.EPFO -> Icons.Rounded.Savings
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("finance_account_${account.accountKey}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.institutionName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${account.maskedNumber} • Updated ${formatShortTime(account.lastUpdated)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(account.lastKnownBalance, account.currencySymbol, masked = !showBalance),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ledger",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = "Open ledger",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FinanceLockGate(
    hasPinConfigured: Boolean,
    onBiometricSuccess: () -> Unit,
    onVerifyPin: (String, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val triggerBiometric = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            val manager = BiometricManager.from(activity)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onBiometricSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            pinError = errString.toString()
                        }
                    }
                )
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock KMS-MS Finance")
                    .setSubtitle("Verify your identity to view balances & ledgers")
                    .setAllowedAuthenticators(authenticators)
                    .build()
                prompt.authenticate(info)
            } else {
                pinError = "Device biometrics unavailable. Use your Finance PIN."
            }
        }
    }

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Finance Vault Locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your financial balances and transaction history are protected on-device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = triggerBiometric,
            modifier = Modifier.fillMaxWidth().testTag("finance_unlock_biometric_button")
        ) {
            Icon(Icons.Rounded.Fingerprint, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Unlock with Biometrics")
        }

        if (hasPinConfigured) {
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = {
                    pinInput = it.filter { ch -> ch.isDigit() }.take(6)
                    pinError = null
                },
                label = { Text("Or enter Finance PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("finance_pin_input")
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    onVerifyPin(pinInput) { ok ->
                        if (!ok) pinError = "Incorrect PIN. Try again."
                    }
                },
                enabled = pinInput.length >= 4,
                modifier = Modifier.fillMaxWidth().testTag("finance_pin_submit_button")
            ) {
                Text("Unlock with PIN")
            }
        }

        pinError?.let { err ->
            Spacer(Modifier.height(10.dp))
            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceLedgerScreen(
    accountKey: String,
    viewModel: KmsMainViewModel,
    onBack: () -> Unit
) {
    val account by remember(accountKey) { viewModel.observeAccount(accountKey) }
        .collectAsStateWithLifecycle(initialValue = null)
    val transactions by remember(accountKey) { viewModel.observeTransactionsForAccount(accountKey) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val semantic = LocalKmsSemanticColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(account?.institutionName ?: "Account Ledger", style = MaterialTheme.typography.titleLarge)
                        account?.let {
                            Text(
                                "${it.accountType.label} • ${it.maskedNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            account?.let { acc ->
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "LATEST AVAILABLE BALANCE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = formatCurrency(acc.lastKnownBalance, acc.currencySymbol, masked = !prefs.showBalance),
                                style = MaterialTheme.typography.displaySmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (transactions.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Rounded.CreditCard,
                        title = "No parsed transactions",
                        subtitle = "Debit and credit SMS alerts for this account will appear chronologically here."
                    )
                }
            } else {
                items(transactions, key = { it.id }) { txn ->
                    LedgerTransactionRow(txn = txn, showBalance = prefs.showBalance)
                }
            }
        }
    }
}

@Composable
private fun LedgerTransactionRow(
    txn: FinanceTransactionEntity,
    showBalance: Boolean
) {
    val semantic = LocalKmsSemanticColors.current
    val isCredit = txn.type == TransactionType.CREDIT
    val amountColor = if (isCredit) semantic.creditGreen else semantic.debitCoral
    val sign = if (isCredit) "+" else "−"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = txn.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = txn.paymentMode,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = txn.expenseCategory,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatFullDateTime(txn.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$sign${formatCurrency(txn.amount, txn.currencySymbol, masked = !showBalance)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = amountColor
                )
                txn.balanceAfter?.let { bal ->
                    Text(
                        text = "Bal ${formatCurrency(bal, txn.currencySymbol, masked = !showBalance)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
