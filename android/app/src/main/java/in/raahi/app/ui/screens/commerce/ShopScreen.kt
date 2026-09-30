package `in`.raahi.app.ui.screens.commerce

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.CommerceRepository
import `in`.raahi.app.network.ShopProductDto
import `in`.raahi.app.ui.components.*
import `in`.raahi.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ShopUiState {
    data object Loading : ShopUiState()
    data class Loaded(val products: List<ShopProductDto>) : ShopUiState()
    data class Error(val message: String) : ShopUiState()
}

private val FIXED_CATEGORIES = listOf("Car Cleaning", "Accessories")

@HiltViewModel
class ShopViewModel @Inject constructor(private val repository: CommerceRepository) : ViewModel() {
    private val _state = MutableStateFlow<ShopUiState>(ShopUiState.Loading)
    val state: StateFlow<ShopUiState> = _state.asStateFlow()
    private var currentCategory: String? = null
    init { refresh() }

    fun refresh(category: String? = currentCategory) {
        currentCategory = category
        _state.value = ShopUiState.Loading
        viewModelScope.launch {
            runCatching { repository.shopProducts(category) }
                .onSuccess { list -> _state.value = ShopUiState.Loaded(list) }
                .onFailure { e -> _state.value = ShopUiState.Error(e.message ?: "Could not load products") }
        }
    }
}

/** Shop, restyled to the concept redesign — glass search bar, gradient chips, glass product
 * rows. Real data/filter logic unchanged from the previous pass. */
@Composable
fun ShopScreen(onBack: () -> Unit, onNavigateTab: (RaahiTab) -> Unit, viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiBg) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize()) {
                    ShopHeader()
                    SearchBar(query) { query = it }
                    CategoryChipRow(selectedCategory) { category -> selectedCategory = category; viewModel.refresh(category) }

                    when (val s = state) {
                        is ShopUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrange) }
                        is ShopUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(s.message, color = RaahiTextDim)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { viewModel.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange)) { Text("Retry") }
                            }
                        }
                        is ShopUiState.Loaded -> {
                            val filtered = remember(s.products, query) {
                                if (query.isBlank()) s.products
                                else s.products.filter { it.name.contains(query, ignoreCase = true) || it.brand?.contains(query, ignoreCase = true) == true }
                            }
                            if (filtered.isEmpty()) EmptyProductsState()
                            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(filtered, key = { it.id }) { product ->
                                    ProductCard(product) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.affiliateUrl))) }
                                }
                            }
                        }
                    }
                }
            }
            RaahiBottomNavBar(current = RaahiTab.SHOP, onSelect = onNavigateTab)
        }
    }
}

@Composable
private fun ShopHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Raahi Shop", color = RaahiText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = RaahiDisplayFont)
            Text("Powered by Amazon", color = RaahiTextDim, fontSize = 10.5.sp)
        }
        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = RaahiTextDim, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text("Car products search karo...", color = RaahiTextDim, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = RaahiTextDim) },
        singleLine = true, shape = RaahiShapePill,
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = RaahiText, unfocusedTextColor = RaahiText,
            focusedContainerColor = RaahiGlass, unfocusedContainerColor = RaahiGlass,
            focusedBorderColor = RaahiBorder, unfocusedBorderColor = RaahiBorder, cursorColor = RaahiOrange,
        ),
    )
}

@Composable
private fun CategoryChipRow(selected: String?, onSelect: (String?) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { RaahiChip("All", selected == null) { onSelect(null) } }
        items(FIXED_CATEGORIES) { c -> RaahiChip(c, selected == c, icon = RaahiIcons.Wrench) { onSelect(c) } }
    }
}

@Composable
private fun EmptyProductsState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = RaahiTextFaint, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(14.dp))
            Text("Koi product nahi mila", color = RaahiTextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProductCard(p: ShopProductDto, onOpen: () -> Unit) {
    RowCard(onClick = onOpen) {
        Column(Modifier.weight(1f)) {
            Text(p.name, color = RaahiText, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            if (p.brand != null) Text(p.brand, color = RaahiTextDim, fontSize = 10.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.discountPrice != null) {
                    Text("₹${p.discountPrice.toInt()}", color = RaahiGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = RaahiDisplayFont)
                    Spacer(Modifier.width(6.dp))
                    Text("₹${p.price.toInt()}", color = RaahiTextFaint, fontSize = 10.5.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                } else {
                    Text("₹${p.price.toInt()}", color = RaahiText, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = RaahiDisplayFont)
                }
                if (p.rating != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Star, contentDescription = null, tint = RaahiAmber, modifier = Modifier.size(11.dp))
                    Text(" ${p.rating}", color = RaahiTextDim, fontSize = 10.5.sp)
                }
            }
        }
        Icon(Icons.Outlined.OpenInNew, contentDescription = "Open on Amazon", tint = RaahiOrange, modifier = Modifier.size(17.dp))
    }
}
