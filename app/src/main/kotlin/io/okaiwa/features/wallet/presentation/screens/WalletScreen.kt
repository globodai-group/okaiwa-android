package io.okaiwa.features.wallet.presentation.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.okaiwa.features.wallet.domain.entities.BlockchainNetwork
import io.okaiwa.features.wallet.domain.entities.ChainWallet
import io.okaiwa.features.wallet.domain.entities.CryptoTransaction
import io.okaiwa.features.wallet.domain.entities.TransactionType
import io.okaiwa.features.wallet.presentation.viewmodels.WalletViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

/**
 * Wallet overview screen.
 *
 * Displays total balance, per-chain wallet cards with native and token
 * balances, and recent transaction history. Supports pull-to-refresh
 * for balance updates.
 */
@Composable
fun WalletScreen(
    onNavigateToSend: (String) -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallet") },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshBalances() },
                        enabled = !uiState.isRefreshing,
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Total balance card
                    item {
                        TotalBalanceCard(
                            totalBalanceUsd = uiState.wallet?.totalBalanceUsd ?: 0.0,
                        )
                    }

                    // Network selector
                    item {
                        NetworkSelector(
                            selectedNetwork = uiState.selectedNetwork,
                            onNetworkSelected = viewModel::selectNetwork,
                        )
                    }

                    // Selected chain card
                    uiState.selectedChainWallet?.let { chainWallet ->
                        item {
                            ChainWalletCard(
                                chainWallet = chainWallet,
                                onSend = { onNavigateToSend(chainWallet.network.name) },
                            )
                        }
                    }

                    // Transaction history
                    item {
                        Text(
                            text = "Recent transactions",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    if (uiState.transactions.isEmpty()) {
                        item {
                            Text(
                                text = "No transactions yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(
                            items = uiState.transactions,
                            key = { it.id },
                        ) { transaction ->
                            TransactionItem(transaction = transaction)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalBalanceCard(totalBalanceUsd: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Total balance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatUsd(totalBalanceUsd),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun NetworkSelector(
    selectedNetwork: BlockchainNetwork,
    onNetworkSelected: (BlockchainNetwork) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(BlockchainNetwork.entries) { network ->
            FilterChip(
                selected = network == selectedNetwork,
                onClick = { onNetworkSelected(network) },
                label = { Text(network.displayName) },
            )
        }
    }
}

@Composable
private fun ChainWalletCard(
    chainWallet: ChainWallet,
    onSend: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chainWallet.network.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatUsd(chainWallet.totalBalanceUsd),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Native balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${chainWallet.nativeBalance} ${chainWallet.network.nativeSymbol}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = formatUsd(chainWallet.nativeBalanceUsd),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Token balances
            chainWallet.tokens.forEach { token ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${token.balance} ${token.symbol}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = formatUsd(token.balanceUsd),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onSend,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send")
                }

                OutlinedButton(
                    onClick = { /* TODO: receive */ },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.CallReceived,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Receive")
                }

                IconButton(onClick = { /* TODO: copy address */ }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy address")
                }
            }

            // Address
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = chainWallet.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TransactionItem(transaction: CryptoTransaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Direction icon
        Icon(
            imageVector = when (transaction.type) {
                TransactionType.Send -> Icons.AutoMirrored.Filled.Send
                TransactionType.Receive -> Icons.Default.CallReceived
                else -> Icons.AutoMirrored.Filled.Send
            },
            contentDescription = null,
            tint = when (transaction.type) {
                TransactionType.Send -> MaterialTheme.colorScheme.error
                TransactionType.Receive -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (transaction.type) {
                    TransactionType.Send -> "Sent"
                    TransactionType.Receive -> "Received"
                    TransactionType.Swap -> "Swap"
                    TransactionType.ContractInteraction -> "Contract"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = transaction.shortHash ?: transaction.status.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            val sign = if (transaction.type == TransactionType.Receive) "+" else "-"
            Text(
                text = "$sign${transaction.amount} ${transaction.tokenSymbol}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = when (transaction.type) {
                    TransactionType.Receive -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            transaction.amountUsd?.let { usd ->
                Text(
                    text = formatUsd(usd),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatUsd(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    format.currency = Currency.getInstance("USD")
    return format.format(amount)
}
